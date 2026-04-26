"use strict";

(function() {
	if (!window.THREE || !THREE.GLTFLoader) {
		maptypes.ModelMapType = function(options) {
			throw "Three.js GLTF support failed to load for ModelMapType";
		};
		return;
	}

	var KEY_CODES = {
		FORWARD: "KeyW",
		BACKWARD: "KeyS",
		LEFT: "KeyA",
		RIGHT: "KeyD",
		UP: "Space",
		UP_ALT: "KeyE",
		DOWN: "ShiftLeft",
		DOWN_ALT: "ShiftRight",
		DOWN_ALT2: "KeyQ",
		BOOST: "ControlLeft",
		BOOST_ALT: "ControlRight"
	};

	var ModelProjection = DynmapProjection.extend({
		fromLocationToLatLng: function(location) {
			return new L.LatLng(-location.z, location.x, location.y);
		},
		fromLatLngToLocation: function(latlng, y) {
			return { x: latlng.lng, y: y, z: -latlng.lat };
		}
	});

	var ModelMapType = L.Layer.extend({
		options: {
			minZoom: 0,
			maxZoom: 6,
			is3dviewer: true
		},

		initialize: function(options) {
			options.minZoom = (typeof options.modelminzoom === "number") ? options.modelminzoom : 0;
			options.maxZoom = (typeof options.modelmaxzoom === "number") ? options.modelmaxzoom : 6;
			options.tileblocksize = options.tileblocksize || 16;
			options.tileformat = options.tileformat || "glb";
			options.compassview = options.compassview || "N";
			options.nightandday = false;

			this.projection = new ModelProjection($.extend({ map: this }, options));
			L.Util.setOptions(this, options);

			this._cameraPosition = new THREE.Vector3(
				options.world.center.x || 0,
				(options.world.center.y || options.world.sealevel || 64) + options.tileblocksize,
				(options.world.center.z || 0) + (options.tileblocksize * 2)
			);
			this._yaw = Math.PI / 2;
			this._pitch = -0.2;
			this._viewDistance = this._distanceFromZoom(options.maxZoom - 1);
			this._moveSpeed = options.tileblocksize * 2.0;
			this._lookSensitivity = 0.0025;
			this._loadedTiles = {};
			this._loadingTiles = {};
			this._tileTimestamps = {};
			this._desiredTiles = {};
			this._keysDown = {};
			this._needsRender = true;
			this._renderLoopActive = false;
			this._pointerLocked = false;
			this._mapEventHandlers = null;
			this._loadCounter = 0;
			this._lastFrameTime = 0;
			this._lastLeafletSync = 0;
			this._lastTileRefresh = null;
			this._fpsSampleStart = 0;
			this._fpsFrameCount = 0;
			this._displayFPS = 0;
			this._fpsAverage = 0;
			this._ignoreLeafletMoveCount = 0;
			this._ignoreLeafletZoomCount = 0;
		},

		onAdd: function(map) {
			this._map = map;
			if (!this._viewerContainer) {
				this._initViewer(map.getContainer());
			}
			this._viewerContainer.style.display = "block";
			this._bindMapEvents();
			this._syncFromLeaflet();
			this._refreshVisibleTiles();
			this._requestRender();
			this._startRenderLoop();
		},

		onRemove: function() {
			this._unbindMapEvents();
			this._clearMovementKeys();
			if (document.pointerLockElement === this._viewerContainer && document.exitPointerLock) {
				document.exitPointerLock();
			}
			if (this._viewerContainer) {
				this._viewerContainer.classList.remove("modelmap-captured");
				this._viewerContainer.style.display = "none";
			}
			if (this._hud) {
				this._hud.style.display = "none";
			}
		},

		getProjection: function() {
			return this.projection;
		},

		updateNamedTile: function(tileName, timestamp) {
			this._tileTimestamps[tileName] = timestamp;
			if (this._desiredTiles[tileName] || this._loadedTiles[tileName] || this._loadingTiles[tileName]) {
				this._reloadTile(tileName);
			}
		},

		_initViewer: function(parent) {
			var me = this;
			me._viewerContainer = document.createElement("div");
			me._viewerContainer.className = "modelmap-view";
			me._viewerContainer.tabIndex = 0;
			parent.appendChild(me._viewerContainer);

			me._hud = document.createElement("div");
			me._hud.className = "modelmap-hud";
			me._hud.innerHTML = '<div class="modelmap-hud-row"><span class="modelmap-hud-label">FPS</span><span class="modelmap-hud-value">0</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Render distance</span><span class="modelmap-hud-value">0 chunks</span></div>';
			parent.appendChild(me._hud);
			me._hudFPS = me._hud.getElementsByClassName("modelmap-hud-value")[0];
			me._hudDistance = me._hud.getElementsByClassName("modelmap-hud-value")[1];

			me._renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
			me._renderer.setPixelRatio(window.devicePixelRatio || 1);
			me._renderer.outputEncoding = THREE.sRGBEncoding;
			me._renderer.toneMapping = THREE.NoToneMapping;
			me._viewerContainer.appendChild(me._renderer.domElement);

			me._scene = new THREE.Scene();
			me._root = new THREE.Group();
			me._scene.add(me._root);

			me._camera = new THREE.PerspectiveCamera(75, 1, 0.1, 50000);
			me._ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
			me._scene.add(me._ambientLight);
			me._sunLight = new THREE.DirectionalLight(0xffffff, 0.8);
			me._sunLight.position.set(0.5, 1.0, 0.25);
			me._scene.add(me._sunLight);
			me._grid = new THREE.GridHelper(2048, 64, 0x555555, 0x333333);
			me._grid.position.y = me.options.world.miny || 0;
			me._scene.add(me._grid);

			me._loader = new THREE.GLTFLoader();

			me._viewerContainer.addEventListener("contextmenu", function(event) {
				event.preventDefault();
			});
			me._viewerContainer.addEventListener("click", function() {
				me._viewerContainer.focus();
				if (me._viewerContainer.requestPointerLock) {
					me._viewerContainer.requestPointerLock();
				}
			});
			me._viewerContainer.addEventListener("wheel", function(event) {
				event.preventDefault();
				var zoomScale = Math.pow(1.1, -event.deltaY / 120.0);
				me._viewDistance = THREE.MathUtils.clamp(me._viewDistance * zoomScale, me.options.tileblocksize * 2.0, 32000);
				me._updateHUD();
				me._syncLeafletFromViewer(true);
				me._refreshVisibleTiles();
				me._requestRender();
			}, { passive: false });

			document.addEventListener("pointerlockchange", function() {
				me._pointerLocked = (document.pointerLockElement === me._viewerContainer);
				me._viewerContainer.classList.toggle("modelmap-captured", me._pointerLocked);
				if (!me._pointerLocked) {
					me._clearMovementKeys();
				}
			});
			document.addEventListener("mousemove", function(event) {
				if (!me._pointerLocked || !me._isViewerActive()) {
					return;
				}
				me._yaw += (event.movementX || 0) * me._lookSensitivity;
				me._pitch = THREE.MathUtils.clamp(
					me._pitch - (event.movementY || 0) * me._lookSensitivity,
					-1.45,
					1.45
				);
				me._requestRender();
			});
			window.addEventListener("keydown", function(event) {
				if (!me._isViewerActive()) {
					return;
				}
				if (me._isMovementKey(event.code)) {
					me._keysDown[event.code] = true;
					me._requestRender();
					event.preventDefault();
				}
			});
			window.addEventListener("keyup", function(event) {
				if (me._isMovementKey(event.code)) {
					delete me._keysDown[event.code];
				}
			});
			window.addEventListener("blur", function() {
				me._clearMovementKeys();
			});

			me._resizeViewer();
			me._updateHUD();
		},

		_bindMapEvents: function() {
			var me = this;
			if (me._mapEventHandlers) {
				return;
			}
			me._mapEventHandlers = {
				moveend: function() {
					if (me._ignoreLeafletMoveCount > 0) {
						me._ignoreLeafletMoveCount--;
						return;
					}
					me._syncFromLeaflet();
					me._refreshVisibleTiles();
					me._requestRender();
				},
				zoomend: function() {
					if (me._ignoreLeafletZoomCount > 0) {
						me._ignoreLeafletZoomCount--;
						return;
					}
					me._syncFromLeaflet();
					me._refreshVisibleTiles();
					me._requestRender();
				},
				resize: function() {
					me._resizeViewer();
					me._refreshVisibleTiles();
					me._requestRender();
				}
			};
			me._map.on("moveend", me._mapEventHandlers.moveend);
			me._map.on("zoomend", me._mapEventHandlers.zoomend);
			me._map.on("resize", me._mapEventHandlers.resize);
		},

		_unbindMapEvents: function() {
			if (!this._map || !this._mapEventHandlers) {
				return;
			}
			this._map.off("moveend", this._mapEventHandlers.moveend);
			this._map.off("zoomend", this._mapEventHandlers.zoomend);
			this._map.off("resize", this._mapEventHandlers.resize);
			this._mapEventHandlers = null;
		},

		_isViewerActive: function() {
			return !!this._viewerContainer && this._viewerContainer.style.display !== "none";
		},

		_isMovementKey: function(code) {
			return code === KEY_CODES.FORWARD ||
				code === KEY_CODES.BACKWARD ||
				code === KEY_CODES.LEFT ||
				code === KEY_CODES.RIGHT ||
				code === KEY_CODES.UP ||
				code === KEY_CODES.UP_ALT ||
				code === KEY_CODES.DOWN ||
				code === KEY_CODES.DOWN_ALT ||
				code === KEY_CODES.DOWN_ALT2 ||
				code === KEY_CODES.BOOST ||
				code === KEY_CODES.BOOST_ALT;
		},

		_clearMovementKeys: function() {
			this._keysDown = {};
		},

		_syncFromLeaflet: function() {
			if (!this._map) {
				return;
			}
			var location = this.projection.fromLatLngToLocation(this._map.getCenter(), this._cameraPosition.y);
			this._cameraPosition.x = location.x;
			this._cameraPosition.z = location.z;
			this._viewDistance = this._distanceFromZoom(this._map.getZoom());
		},

		_syncLeafletFromViewer: function(force) {
			if (!this._map) {
				return;
			}
			var now = Date.now();
			if (!force && ((now - this._lastLeafletSync) < 100)) {
				return;
			}
			this._lastLeafletSync = now;
			this._ignoreLeafletMoveCount++;
			this._ignoreLeafletZoomCount++;
			this._map.setView(
				this.projection.fromLocationToLatLng(this._cameraPosition),
				this._zoomFromDistance(this._viewDistance),
				{ animate: false }
			);
		},

		_distanceFromZoom: function(zoom) {
			var zoomDelta = this.options.maxZoom - zoom;
			return this.options.tileblocksize * Math.pow(2, zoomDelta) * 2.0;
		},

		_zoomFromDistance: function(distance) {
			return THREE.MathUtils.clamp(
				this.options.maxZoom - (Math.log(distance / (this.options.tileblocksize * 2.0)) / Math.log(2)),
				this.options.minZoom,
				this.options.maxZoom
			);
		},

		_resizeViewer: function() {
			if (!this._viewerContainer || !this._renderer || !this._camera) {
				return;
			}
			var width = this._viewerContainer.clientWidth || 1;
			var height = this._viewerContainer.clientHeight || 1;
			this._renderer.setSize(width, height, false);
			this._camera.aspect = width / height;
			this._camera.updateProjectionMatrix();
		},

		_requestRender: function() {
			this._needsRender = true;
		},

		_startRenderLoop: function() {
			var me = this;
			if (me._renderLoopActive) {
				return;
			}
			me._renderLoopActive = true;
			me._lastFrameTime = 0;
			me._fpsSampleStart = 0;
			me._fpsFrameCount = 0;
			me._fpsAverage = 0;
			(function renderLoop(frameTime) {
				if (!me._viewerContainer || me._viewerContainer.style.display === "none") {
					me._renderLoopActive = false;
					return;
				}
				window.requestAnimationFrame(renderLoop);
				var previousFrameTime = me._lastFrameTime;
				var deltaSeconds = 0;
				if (previousFrameTime > 0) {
					deltaSeconds = Math.min(0.1, (frameTime - previousFrameTime) / 1000.0);
				}
				var changed = me._updateMovement(deltaSeconds);
				if (changed) {
					me._needsRender = true;
				}
				me._renderScene();
				me._updateFPS(frameTime, previousFrameTime);
				me._lastFrameTime = frameTime;
				me._needsRender = false;
			})();
		},

		_updateMovement: function(deltaSeconds) {
			if (deltaSeconds <= 0) {
				return false;
			}

			var direction = new THREE.Vector3();
			var moved = false;
			var forward = this._getForwardVector();
			var right = this._getRightVector();

			if (this._keysDown[KEY_CODES.FORWARD]) {
				direction.add(forward);
			}
			if (this._keysDown[KEY_CODES.BACKWARD]) {
				direction.sub(forward);
			}
			if (this._keysDown[KEY_CODES.RIGHT]) {
				direction.add(right);
			}
			if (this._keysDown[KEY_CODES.LEFT]) {
				direction.sub(right);
			}
			if (this._keysDown[KEY_CODES.UP] || this._keysDown[KEY_CODES.UP_ALT]) {
				direction.y += 1;
			}
			if (this._keysDown[KEY_CODES.DOWN] || this._keysDown[KEY_CODES.DOWN_ALT] || this._keysDown[KEY_CODES.DOWN_ALT2]) {
				direction.y -= 1;
			}

			if (direction.lengthSq() > 0) {
				direction.normalize();
				var speed = this._moveSpeed;
				if (this._keysDown[KEY_CODES.BOOST] || this._keysDown[KEY_CODES.BOOST_ALT]) {
					speed *= 4.0;
				}
				this._cameraPosition.addScaledVector(direction, speed * deltaSeconds);
				this._cameraPosition.y = Math.max((this.options.world.miny || 0) + 1, this._cameraPosition.y);
				this._syncLeafletFromViewer(false);
				this._refreshVisibleTiles();
				moved = true;
			}

			if (moved) {
				this._requestRender();
			}
			return moved;
		},

		_getForwardVector: function() {
			var cosPitch = Math.cos(this._pitch);
			return new THREE.Vector3(
				-cosPitch * Math.cos(this._yaw),
				Math.sin(this._pitch),
				-cosPitch * Math.sin(this._yaw)
			).normalize();
		},

		_getRightVector: function() {
			return new THREE.Vector3(
				Math.sin(this._yaw),
				0,
				-Math.cos(this._yaw)
			).normalize();
		},

		_renderScene: function() {
			var forward = this._getForwardVector();
			this._camera.position.copy(this._cameraPosition);
			this._camera.lookAt(this._cameraPosition.clone().add(forward));
			this._grid.position.x = Math.round(this._cameraPosition.x / this.options.tileblocksize) * this.options.tileblocksize;
			this._grid.position.z = Math.round(this._cameraPosition.z / this.options.tileblocksize) * this.options.tileblocksize;
			this._renderer.render(this._scene, this._camera);
			this._updateHUD();
		},

		_updateFPS: function(frameTime, previousFrameTime) {
			if (previousFrameTime > 0) {
				var frameFPS = 1000.0 / Math.max(1.0, frameTime - previousFrameTime);
				if (this._fpsAverage <= 0) {
					this._fpsAverage = frameFPS;
				}
				else {
					this._fpsAverage = (this._fpsAverage * 0.9) + (frameFPS * 0.1);
				}
				var roundedFPS = Math.round(this._fpsAverage);
				if (roundedFPS !== this._displayFPS) {
					this._displayFPS = roundedFPS;
					this._updateHUD();
				}
			}
		},

		_updateHUD: function() {
			if (!this._hud) {
				return;
			}
			this._hud.style.display = this._isViewerActive() ? "block" : "none";
			if (this._hudFPS) {
				this._hudFPS.textContent = String(this._displayFPS);
			}
			if (this._hudDistance) {
				this._hudDistance.textContent = Math.max(1, Math.round(this._viewDistance / 16)) + " chunks";
			}
		},

		onSelectedMap: function(previousMap, previousLocation) {
			if (!previousMap || (typeof previousMap.options.azimuth !== "number") || (typeof previousMap.options.inclination !== "number")) {
				return;
			}

			var focusLocation = previousLocation;
			if (!focusLocation && this._map) {
				focusLocation = this.projection.fromLatLngToLocation(this._map.getCenter(), this._cameraPosition.y);
			}
			if (!focusLocation) {
				return;
			}

			this._yaw = THREE.MathUtils.degToRad(90.0 + previousMap.options.azimuth);
			this._pitch = -THREE.MathUtils.degToRad(previousMap.options.inclination);

			var forward = this._getForwardVector();
			var initialDistance = Math.max(this.options.tileblocksize * 6.0, this._viewDistance);
			this._cameraPosition.set(focusLocation.x, focusLocation.y, focusLocation.z);
			this._cameraPosition.addScaledVector(forward, -initialDistance);
			this._cameraPosition.y = Math.max((this.options.world.miny || 0) + 1, this._cameraPosition.y);
			this._refreshVisibleTiles();
			this._requestRender();
			this._syncLeafletFromViewer(true);
		},

		_refreshVisibleTiles: function() {
			var radius = Math.max(1, Math.min(8, Math.ceil((this._viewDistance * Math.tan(THREE.MathUtils.degToRad(this._camera.fov * 0.5))) / this.options.tileblocksize)));
			var centerTileX = Math.floor(this._cameraPosition.x / this.options.tileblocksize);
			var centerTileZ = Math.floor(this._cameraPosition.z / this.options.tileblocksize);
			var refreshKey = centerTileX + ":" + centerTileZ + ":" + radius;
			if (this._lastTileRefresh === refreshKey) {
				return;
			}
			this._lastTileRefresh = refreshKey;

			var wanted = {};
			var x;
			var z;

			for (x = centerTileX - radius; x <= centerTileX + radius; x++) {
				for (z = centerTileZ - radius; z <= centerTileZ + radius; z++) {
					var tileName = this._getTileName(x, z);
					wanted[tileName] = { x: x, z: z };
					if (!this._loadedTiles[tileName] && !this._loadingTiles[tileName]) {
						this._loadTile(tileName, x, z);
					}
				}
			}

			for (var existing in this._loadedTiles) {
				if (!Object.prototype.hasOwnProperty.call(this._loadedTiles, existing)) {
					continue;
				}
				if (!wanted[existing]) {
					this._unloadTile(existing);
				}
			}
			this._desiredTiles = wanted;
		},

		_configureTexture: function(texture) {
			if (!texture) {
				return;
			}
			texture.magFilter = THREE.NearestFilter;
			texture.minFilter = THREE.NearestFilter;
			texture.generateMipmaps = false;
			texture.anisotropy = 1;
			texture.needsUpdate = true;
		},

		_createDisplayMaterial: function(material) {
			var displayMaterial = new THREE.MeshLambertMaterial({
				color: material.color ? material.color.clone() : new THREE.Color(0xffffff),
				map: material.map || null,
				transparent: material.transparent,
				opacity: material.opacity,
				alphaTest: material.alphaTest,
				side: material.side,
				vertexColors: material.vertexColors,
				fog: false,
				emissive: material.emissive ? material.emissive.clone() : new THREE.Color(0x000000),
				emissiveIntensity: (typeof material.emissiveIntensity === "number") ? material.emissiveIntensity : 1.0
			});
			displayMaterial.depthWrite = !displayMaterial.transparent;
			if (displayMaterial.map) {
				displayMaterial.map.encoding = THREE.sRGBEncoding;
				this._configureTexture(displayMaterial.map);
			}
			displayMaterial.needsUpdate = true;
			return displayMaterial;
		},

		_prepareSceneForDisplay: function(scene) {
			var me = this;
			scene.traverse(function(node) {
				if (!node.isMesh) {
					return;
				}
				node.frustumCulled = false;
				if (node.geometry && !node.geometry.attributes.normal) {
					node.geometry.computeVertexNormals();
					node.geometry.normalizeNormals();
					node.geometry.attributes.normal.needsUpdate = true;
				}
				if ($.isArray(node.material)) {
					node.material = $.map(node.material, function(material) {
						return me._createDisplayMaterial(material);
					});
				}
				else if (node.material) {
					node.material = me._createDisplayMaterial(node.material);
				}
			});
		},

		_loadTile: function(tileName, tileX, tileZ) {
			var me = this;
			var token = ++me._loadCounter;
			var timestamp = me._tileTimestamps[tileName];
			var url = me._getTileUrl(tileName, timestamp);
			me._loadingTiles[tileName] = token;
			me._loader.load(url, function(gltf) {
				if (me._loadingTiles[tileName] !== token) {
					return;
				}
				delete me._loadingTiles[tileName];
				if (!me._desiredTiles[tileName]) {
					return;
				}
				me._unloadTile(tileName);
				var scene = gltf.scene || new THREE.Group();
				me._prepareSceneForDisplay(scene);
				scene.position.set(me._getTileCenter(tileX), me.options.world.miny || 0, me._getTileCenter(tileZ));
				me._loadedTiles[tileName] = {
					object: scene,
					x: tileX,
					z: tileZ
				};
				me._root.add(scene);
				me._requestRender();
			}, undefined, function() {
				if (me._loadingTiles[tileName] === token) {
					delete me._loadingTiles[tileName];
				}
			});
		},

		_reloadTile: function(tileName) {
			var tile = this._desiredTiles[tileName] || this._loadedTiles[tileName];
			if (!tile) {
				return;
			}
			delete this._loadingTiles[tileName];
			this._loadTile(tileName, tile.x, tile.z);
		},

		_unloadTile: function(tileName) {
			var existing = this._loadedTiles[tileName];
			if (!existing) {
				return;
			}
			this._disposeObject(existing.object);
			this._root.remove(existing.object);
			delete this._loadedTiles[tileName];
			this._requestRender();
		},

		_disposeObject: function(object) {
			object.traverse(function(node) {
				if (node.geometry) {
					node.geometry.dispose();
				}
				if (node.material) {
					if ($.isArray(node.material)) {
						$.each(node.material, function(idx, material) {
							if (material.map) {
								material.map.dispose();
							}
							material.dispose();
						});
					}
					else {
						if (node.material.map) {
							node.material.map.dispose();
						}
						node.material.dispose();
					}
				}
			});
		},

		_getTileCenter: function(tileCoord) {
			return (tileCoord * this.options.tileblocksize) + ((this.options.tileblocksize - 1) / 2.0);
		},

		_getTileName: function(tileX, tileZ) {
			return namedReplace("{prefix}/{bucketx}_{bucketz}/{x}_{z}.{fmt}", {
				prefix: this.options.prefix,
				bucketx: Math.floor(tileX / 32),
				bucketz: Math.floor(tileZ / 32),
				x: tileX,
				z: tileZ,
				fmt: this.options.tileformat
			});
		},

		_getTileUrl: function(tileName, timestamp) {
			var url = this.options.dynmap.getTileUrl(tileName);
			var value = (typeof timestamp === "undefined") ? this.options.dynmap.inittime : timestamp;
			if (typeof value !== "undefined") {
				url += (url.indexOf("?") === -1 ? "?timestamp=" + value : "&timestamp=" + value);
			}
			return url;
		}
	});

	maptypes.ModelMapType = function(options) { return new ModelMapType(options); };
})();
