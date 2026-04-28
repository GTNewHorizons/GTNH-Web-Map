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
		BOOST_ALT: "ControlRight",
		SPEED_UP: "Equal",
		SPEED_UP_ALT: "NumpadAdd",
		SPEED_DOWN: "Minus",
		SPEED_DOWN_ALT: "NumpadSubtract",
		NIGHTVISION: "KeyN",
		VIEW_DISTANCE_UP: "PageUp",
		VIEW_DISTANCE_DOWN: "PageDown"
	};
	var DEFAULT_DAY_AMBIENT_LIGHT = 0.7;
	var DEFAULT_NIGHT_AMBIENT_LIGHT = 0.14;
	var DEFAULT_DAY_SUN_LIGHT = 0.8;
	var DEFAULT_NIGHT_SUN_LIGHT = 0.16;
	var MAX_RENDER_PIXEL_RATIO = 2.0;
	var HUD_UPDATE_INTERVAL_MS = 100;
	var MAX_CONCURRENT_TILE_LOADS = 4;

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
			options.lightingmode = this._normalizeLightingMode(options.lightingmode);
			options.ambientlightday = this._resolveLightSetting(options.ambientlightday, DEFAULT_DAY_AMBIENT_LIGHT);
			options.ambientlightnight = this._resolveLightSetting(options.ambientlightnight, DEFAULT_NIGHT_AMBIENT_LIGHT);
			options.sunlightday = this._resolveLightSetting(options.sunlightday, DEFAULT_DAY_SUN_LIGHT);
			options.sunlightnight = this._resolveLightSetting(options.sunlightnight, DEFAULT_NIGHT_SUN_LIGHT);

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
			this._moveSpeed = options.tileblocksize;
			this._minMoveSpeed = Math.max(1.0, options.tileblocksize * 0.25);
			this._maxMoveSpeed = options.tileblocksize * 16.0;
			this._moveSpeedStep = 1.25;
			this._lookSensitivity = 0.0025;
			this._loadedTiles = {};
			this._loadingTiles = {};
			this._pendingTileLoads = [];
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
			this._pendingLeafletMoveEvents = 0;
			this._pendingLeafletZoomEvents = 0;
			this._lastSyncedLeafletCenter = null;
			this._lastSyncedLeafletZoom = null;
			this._nightVisionEnabled = false;
			this._currentAmbientIntensity = null;
			this._currentSunIntensity = null;
			this._lastMaterialLightingBlend = null;
			this._hudState = {};
			this._hudVisible = null;
			this._lastHUDUpdateAt = 0;
			this._frustum = new THREE.Frustum();
			this._frustumMatrix = new THREE.Matrix4();
			this._tileCullBounds = new THREE.Box3();
			this._tileCullMin = new THREE.Vector3();
			this._tileCullMax = new THREE.Vector3();
			this._serverTime = (typeof options.dynmap.servertime === "number") ? options.dynmap.servertime : 0;
			this._serverTimeCapturedAt = Date.now();
			this._bindDynmapEvents();
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

		_bindDynmapEvents: function() {
			var me = this;
			if (me._dynmapEventsBound || !me.options.dynmap) {
				return;
			}
			me._dynmapEventsBound = true;
			$(me.options.dynmap).bind("worldupdated.modelmap", function(event, update) {
				if (typeof update.servertime === "number") {
					me._serverTime = update.servertime;
					me._serverTimeCapturedAt = Date.now();
				}
				me._applySceneLighting();
				me._updateTileLighting();
				me._requestRender();
			});
		},

		_normalizeLightingMode: function(mode) {
			if (mode === "night" || mode === "both") {
				return mode;
			}
			return "day";
		},

		_resolveLightSetting: function(value, defaultValue) {
			return (typeof value === "number" && value >= 0) ? value : defaultValue;
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
			me._hud.innerHTML = '<div class="modelmap-hud-row"><span class="modelmap-hud-label">FPS</span><span class="modelmap-hud-value" data-field="fps">0</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Render distance</span><span class="modelmap-hud-value" data-field="distance">0 chunks</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Move speed</span><span class="modelmap-hud-value" data-field="speed">0 blocks/s</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Direction</span><span class="modelmap-hud-value" data-field="direction">N</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Camera</span><span class="modelmap-hud-value" data-field="position">0, 0, 0</span></div>'
				+ '<div class="modelmap-hud-row"><span class="modelmap-hud-label">Chunk</span><span class="modelmap-hud-value" data-field="chunk">0, 0</span></div>'
				+ '<div class="modelmap-hud-row modelmap-hud-row-stack"><span class="modelmap-hud-label">Tile GLB</span><span class="modelmap-hud-value" data-field="tile">-</span></div>';
			parent.appendChild(me._hud);
			me._hudFPS = me._hud.querySelector("[data-field='fps']");
			me._hudDistance = me._hud.querySelector("[data-field='distance']");
			me._hudSpeed = me._hud.querySelector("[data-field='speed']");
			me._hudDirection = me._hud.querySelector("[data-field='direction']");
			me._hudPosition = me._hud.querySelector("[data-field='position']");
			me._hudChunk = me._hud.querySelector("[data-field='chunk']");
			me._hudTile = me._hud.querySelector("[data-field='tile']");

			me._renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
			me._renderer.setPixelRatio(Math.min(MAX_RENDER_PIXEL_RATIO, window.devicePixelRatio || 1));
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
			me._applySceneLighting();

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
				me._adjustViewDistanceFromWheel(event.deltaY);
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
				me._refreshVisibleTiles();
				me._requestRender();
			});
			document.addEventListener("wheel", function(event) {
				if (!me._pointerLocked || !me._isViewerActive()) {
					return;
				}
				event.preventDefault();
				me._adjustViewDistanceFromWheel(event.deltaY);
			}, { passive: false });
			window.addEventListener("keydown", function(event) {
				if (!me._isViewerActive() || !me._pointerLocked) {
					return;
				}
				if (me._isViewDistanceAdjustKey(event)) {
					if (!event.repeat) {
						me._adjustViewDistanceStep(me._isViewDistanceIncreaseKey(event) ? 16.0 : -16.0);
					}
					event.preventDefault();
					return;
				}
				if (me._isNightVisionToggleKey(event)) {
					if (!event.repeat) {
						me._toggleNightVision();
					}
					event.preventDefault();
					return;
				}
				if (me._isSpeedAdjustKey(event.code)) {
					if (!event.repeat) {
						me._adjustMoveSpeed(event.code === KEY_CODES.SPEED_UP || event.code === KEY_CODES.SPEED_UP_ALT ? 1 : -1);
					}
					event.preventDefault();
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
			me._updateHUD(true);
		},

		_bindMapEvents: function() {
			var me = this;
			if (me._mapEventHandlers) {
				return;
			}
			me._mapEventHandlers = {
				moveend: function() {
					if (me._consumeLeafletMoveEvent()) {
						return;
					}
					me._clearPendingLeafletEvents();
					me._syncFromLeaflet();
					me._refreshVisibleTiles();
					me._requestRender();
				},
				zoomend: function() {
					if (me._consumeLeafletZoomEvent()) {
						return;
					}
					me._clearPendingLeafletEvents();
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

		_isSpeedAdjustKey: function(code) {
			return code === KEY_CODES.SPEED_UP ||
				code === KEY_CODES.SPEED_UP_ALT ||
				code === KEY_CODES.SPEED_DOWN ||
				code === KEY_CODES.SPEED_DOWN_ALT;
		},

		_isViewDistanceAdjustKey: function(event) {
			return this._isViewDistanceIncreaseKey(event) || this._isViewDistanceDecreaseKey(event);
		},

		_isViewDistanceIncreaseKey: function(event) {
			return event.code === KEY_CODES.VIEW_DISTANCE_UP || event.key === "PageUp";
		},

		_isViewDistanceDecreaseKey: function(event) {
			return event.code === KEY_CODES.VIEW_DISTANCE_DOWN || event.key === "PageDown";
		},

		_isNightVisionToggleKey: function(event) {
			return event.code === KEY_CODES.NIGHTVISION || event.key === "n" || event.key === "N";
		},

		_clearMovementKeys: function() {
			this._keysDown = {};
		},

		_adjustMoveSpeed: function(direction) {
			var scale = direction > 0 ? this._moveSpeedStep : (1.0 / this._moveSpeedStep);
			this._moveSpeed = THREE.MathUtils.clamp(this._moveSpeed * scale, this._minMoveSpeed, this._maxMoveSpeed);
			this._updateHUD(true);
			this._requestRender();
		},

		_toggleNightVision: function() {
			this._nightVisionEnabled = !this._nightVisionEnabled;
			this._updateTileLighting(true);
			this._requestRender();
		},

		_adjustViewDistanceFromWheel: function(deltaY) {
			var zoomScale = Math.pow(1.1, -deltaY / 120.0);
			this._setViewDistance(this._viewDistance * zoomScale);
		},

		_adjustViewDistanceStep: function(deltaBlocks) {
			this._setViewDistance(this._viewDistance + deltaBlocks);
		},

		_setViewDistance: function(distance) {
			this._viewDistance = THREE.MathUtils.clamp(distance, this.options.tileblocksize * 2.0, 32000);
			this._updateHUD(true);
			this._refreshVisibleTiles();
			this._requestRender();
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
			var targetCenter = this.projection.fromLocationToLatLng(this._cameraPosition);
			var targetZoom = this._zoomFromDistance(this._viewDistance);
			var currentCenter = this._map.getCenter();
			var currentZoom = this._map.getZoom();
			var centerChanged = !this._sameLatLng(currentCenter, targetCenter);
			var zoomChanged = !this._sameZoom(currentZoom, targetZoom);
			if (!centerChanged && !zoomChanged) {
				return;
			}
			this._lastLeafletSync = now;
			if (centerChanged) {
				this._pendingLeafletMoveEvents++;
				this._lastSyncedLeafletCenter = targetCenter;
			}
			if (zoomChanged) {
				this._pendingLeafletZoomEvents++;
				this._lastSyncedLeafletZoom = targetZoom;
			}
			this._map.setView(
				targetCenter,
				targetZoom,
				{ animate: false }
			);
		},

		_clearPendingLeafletEvents: function() {
			this._pendingLeafletMoveEvents = 0;
			this._pendingLeafletZoomEvents = 0;
		},

		_consumeLeafletMoveEvent: function() {
			if (this._pendingLeafletMoveEvents <= 0 || !this._map) {
				return false;
			}
			if (this._pointerLocked) {
				this._pendingLeafletMoveEvents--;
				return true;
			}
			if (!this._lastSyncedLeafletCenter || !this._sameLatLng(this._map.getCenter(), this._lastSyncedLeafletCenter)) {
				return false;
			}
			this._pendingLeafletMoveEvents--;
			if (this._pendingLeafletMoveEvents <= 0) {
				this._lastSyncedLeafletCenter = null;
			}
			return true;
		},

		_consumeLeafletZoomEvent: function() {
			if (this._pendingLeafletZoomEvents <= 0 || !this._map) {
				return false;
			}
			if (this._pointerLocked) {
				this._pendingLeafletZoomEvents--;
				return true;
			}
			if ((typeof this._lastSyncedLeafletZoom !== "number") || !this._sameZoom(this._map.getZoom(), this._lastSyncedLeafletZoom)) {
				return false;
			}
			this._pendingLeafletZoomEvents--;
			if (this._pendingLeafletZoomEvents <= 0) {
				this._lastSyncedLeafletZoom = null;
			}
			return true;
		},

		_sameLatLng: function(a, b) {
			return Math.abs(a.lat - b.lat) < 0.000001 && Math.abs(a.lng - b.lng) < 0.000001;
		},

		_sameZoom: function(a, b) {
			return Math.abs(a - b) < 0.000001;
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
				if (!changed && !me._needsRender && !me._isLightingTransitionActive()) {
					me._lastFrameTime = frameTime;
					return;
				}
				me._renderScene();
				me._updateFPS(frameTime, previousFrameTime);
				me._lastFrameTime = frameTime;
				me._needsRender = false;
			})();
		},

		_updateMovement: function(deltaSeconds) {
			if (deltaSeconds <= 0 || !this._pointerLocked) {
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

		_syncCameraTransform: function() {
			var forward = this._getForwardVector();
			this._camera.position.copy(this._cameraPosition);
			this._camera.lookAt(this._cameraPosition.clone().add(forward));
			this._camera.updateMatrixWorld(true);
			this._frustumMatrix.multiplyMatrices(this._camera.projectionMatrix, this._camera.matrixWorldInverse);
			this._frustum.setFromProjectionMatrix(this._frustumMatrix);
			return forward;
		},

		_getTileRefreshOrientationKey: function() {
			return Math.round(this._yaw * 8.0 / Math.PI) + ":" + Math.round(this._pitch * 8.0 / Math.PI);
		},

		_renderScene: function() {
			this._applySceneLighting();
			this._updateTileLighting();
			this._syncCameraTransform();
			this._grid.position.x = Math.round(this._cameraPosition.x / this.options.tileblocksize) * this.options.tileblocksize;
			this._grid.position.z = Math.round(this._cameraPosition.z / this.options.tileblocksize) * this.options.tileblocksize;
			this._renderer.render(this._scene, this._camera);
			this._updateHUD(false);
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
					this._updateHUD(true);
				}
			}
		},

		_updateHUD: function(force) {
			if (!this._hud) {
				return;
			}
			var now = Date.now();
			if (!force && ((now - this._lastHUDUpdateAt) < HUD_UPDATE_INTERVAL_MS)) {
				return;
			}
			this._lastHUDUpdateAt = now;
			var visible = this._isViewerActive();
			if (this._hudVisible !== visible) {
				this._hudVisible = visible;
				this._hud.style.display = visible ? "block" : "none";
			}
			var tileX = Math.floor(this._cameraPosition.x / this.options.tileblocksize);
			var tileZ = Math.floor(this._cameraPosition.z / this.options.tileblocksize);
			var tileName = this._getTileName(tileX, tileZ);
			this._setHUDField("fps", this._hudFPS, String(this._displayFPS));
			this._setHUDField("distance", this._hudDistance, Math.max(1, Math.round(this._viewDistance / 16)) + " chunks");
			this._setHUDField("speed", this._hudSpeed, this._formatHUDNumber(this._moveSpeed) + " blocks/s");
			this._setHUDField("direction", this._hudDirection, this._getViewDirection());
			this._setHUDField("position", this._hudPosition, this._formatHUDNumber(this._cameraPosition.x)
				+ ", " + this._formatHUDNumber(this._cameraPosition.y)
				+ ", " + this._formatHUDNumber(this._cameraPosition.z));
			this._setHUDField("chunk", this._hudChunk, Math.floor(this._cameraPosition.x / 16)
				+ ", " + Math.floor(this._cameraPosition.z / 16));
			this._setHUDField("tile", this._hudTile, tileName);
		},

		_setHUDField: function(key, element, value) {
			if (!element || this._hudState[key] === value) {
				return;
			}
			this._hudState[key] = value;
			element.textContent = value;
		},

		_formatHUDNumber: function(value) {
			return value.toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1");
		},

		_getViewDirection: function() {
			var forward = this._getForwardVector();
			var directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
			var angle = Math.atan2(forward.x, -forward.z);
			var index = Math.round(angle / (Math.PI / 4));
			index = ((index % directions.length) + directions.length) % directions.length;
			return directions[index];
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
			var radius = Math.max(1, Math.min(8, Math.ceil((this._viewDistance * Math.tan(THREE.MathUtils.degToRad(this._camera.fov * 0.5))) / this.options.tileblocksize) + 1));
			var centerTileX = Math.floor(this._cameraPosition.x / this.options.tileblocksize);
			var centerTileZ = Math.floor(this._cameraPosition.z / this.options.tileblocksize);
			var refreshKey = centerTileX + ":" + centerTileZ + ":" + radius + ":" + Math.round(this._viewDistance / 16)
				+ ":" + this._getTileRefreshOrientationKey();
			if (this._lastTileRefresh === refreshKey) {
				return;
			}
			this._lastTileRefresh = refreshKey;
			this._syncCameraTransform();

			var wanted = {};
			var pendingLoads = [];
			var x;
			var z;
			var maxDistance = this._viewDistance + this.options.tileblocksize * 1.5;

			for (x = centerTileX - radius; x <= centerTileX + radius; x++) {
				for (z = centerTileZ - radius; z <= centerTileZ + radius; z++) {
					var tilePriority = this._getTilePriorityData(x, z, maxDistance);
					if (!tilePriority) {
						continue;
					}
					wanted[tilePriority.tileName] = { x: x, z: z };
					if (!this._loadedTiles[tilePriority.tileName] && !this._loadingTiles[tilePriority.tileName]) {
						pendingLoads.push(tilePriority);
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
			pendingLoads.sort(function(a, b) {
				if (a.inFrustum !== b.inFrustum) {
					return a.inFrustum ? -1 : 1;
				}
				return a.distanceSq - b.distanceSq;
			});
			this._pendingTileLoads = pendingLoads;
			this._pumpTileLoads();
		},

		_getTilePriorityData: function(tileX, tileZ, maxDistance) {
			var centerX = this._getTileCenter(tileX);
			var centerZ = this._getTileCenter(tileZ);
			var halfSize = this.options.tileblocksize * 0.5;
			var maxRadius = maxDistance + halfSize;
			var dx = centerX - this._cameraPosition.x;
			var dz = centerZ - this._cameraPosition.z;
			var distanceSq = (dx * dx) + (dz * dz);
			if (distanceSq > (maxRadius * maxRadius)) {
				return null;
			}
			this._tileCullMin.set(centerX - halfSize, this.options.world.miny || 0, centerZ - halfSize);
			this._tileCullMax.set(centerX + halfSize,
				(typeof this.options.world.worldheight === "number") ? this.options.world.worldheight : (this.options.world.sealevel || 64) + 128,
				centerZ + halfSize);
			this._tileCullBounds.min.copy(this._tileCullMin);
			this._tileCullBounds.max.copy(this._tileCullMax);
			return {
				tileName: this._getTileName(tileX, tileZ),
				x: tileX,
				z: tileZ,
				distanceSq: distanceSq,
				inFrustum: this._frustum.intersectsBox(this._tileCullBounds)
			};
		},

		_pumpTileLoads: function() {
			while (this._pendingTileLoads.length > 0 && Object.keys(this._loadingTiles).length < MAX_CONCURRENT_TILE_LOADS) {
				var next = this._pendingTileLoads.shift();
				if (!next || !this._desiredTiles[next.tileName] || this._loadedTiles[next.tileName] || this._loadingTiles[next.tileName]) {
					continue;
				}
				this._loadTile(next.tileName, next.x, next.z);
			}
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

		_getCurrentServerTime: function() {
			if (typeof this._serverTime !== "number" || this._serverTime < 0) {
				return -1;
			}
			var elapsedSeconds = (Date.now() - this._serverTimeCapturedAt) / 1000.0;
			var servertime = this._serverTime + (elapsedSeconds * 20.0);
			servertime %= 24000.0;
			if (servertime < 0) {
				servertime += 24000.0;
			}
			return servertime;
		},

		_getLightingBlend: function() {
			if (this.options.lightingmode === "night") {
				return 0.0;
			}
			if (this.options.lightingmode !== "both") {
				return 1.0;
			}
			var servertime = this._getCurrentServerTime();
			if (servertime < 0) {
				return 1.0;
			}
			if (servertime >= 2000.0 && servertime < 11000.0) {
				return 1.0;
			}
			if (servertime >= 14000.0 && servertime < 23000.0) {
				return 0.0;
			}
			if (servertime >= 11000.0 && servertime < 14000.0) {
				return this._applyDuskLightingBlendCurve((servertime - 11000.0) / 3000.0);
			}
			var dawnProgress = (servertime >= 23000.0) ? (servertime - 23000.0) : (servertime + 1000.0);
			return this._applyLightingBlendCurve(dawnProgress / 3000.0);
		},

		_applyLightingBlendCurve: function(progress) {
			return Math.pow(THREE.MathUtils.clamp(progress, 0.0, 1.0), 2.2);
		},

		_applyDuskLightingBlendCurve: function(progress) {
			return Math.pow(1.0 - THREE.MathUtils.clamp(progress, 0.0, 1.0), 2.2);
		},

		_isLightingTransitionActive: function() {
			if (this.options.lightingmode !== "both") {
				return false;
			}
			var servertime = this._getCurrentServerTime();
			if (servertime < 0) {
				return false;
			}
			return servertime >= 23000.0 || servertime < 2000.0 || (servertime >= 11000.0 && servertime < 14000.0);
		},

		_applySceneLighting: function() {
			if (!this._ambientLight || !this._sunLight) {
				return;
			}
			var ambient = this.options.ambientlightday;
			var sun = this.options.sunlightday;
			if (this._currentAmbientIntensity !== ambient) {
				this._ambientLight.intensity = ambient;
				this._currentAmbientIntensity = ambient;
			}
			if (this._currentSunIntensity !== sun) {
				this._sunLight.intensity = sun;
				this._currentSunIntensity = sun;
			}
		},

		_updateMaterialLightingUniforms: function(material, blend) {
			if (material && material.userData && material.userData.modelmapLightShader) {
				material.userData.modelmapLightShader.uniforms.modelLightBlend.value = blend;
				material.userData.modelmapLightShader.uniforms.modelNightVision.value = this._nightVisionEnabled ? 1.0 : 0.0;
			}
		},

		_updateTileLighting: function(force) {
			if (!this._root) {
				return;
			}
			var blend = this._getLightingBlend();
			if (!force && this._lastMaterialLightingBlend === blend) {
				return;
			}
			this._lastMaterialLightingBlend = blend;
			this._root.traverse(function(node) {
				if (!node.isMesh || !node.material) {
					return;
				}
				if ($.isArray(node.material)) {
					$.each(node.material, function(idx, material) {
						this._updateMaterialLightingUniforms(material, blend);
					}.bind(this));
				}
				else {
					this._updateMaterialLightingUniforms(node.material, blend);
				}
			}.bind(this));
		},

		_applyBakedLightingShader: function(material, geometry) {
			var hasNightLight = !!(geometry && geometry.attributes && geometry.attributes._nightlight);
			material.customProgramCacheKey = function() {
				return "modelmap-baked-lighting-v2:" + (hasNightLight ? "1" : "0");
			};
			material.onBeforeCompile = function(shader) {
				shader.uniforms.modelLightBlend = { value: this._getLightingBlend() };
				shader.uniforms.modelNightVision = { value: this._nightVisionEnabled ? 1.0 : 0.0 };
				material.userData.modelmapLightShader = shader;
				if (hasNightLight) {
					shader.vertexShader = "attribute float _nightlight;\nvarying float vNightLight;\n" + shader.vertexShader;
					shader.vertexShader = shader.vertexShader.replace(
						"#include <color_vertex>",
						"#include <color_vertex>\n\tvNightLight = _nightlight;"
					);
				}
				shader.fragmentShader = "uniform float modelLightBlend;\nuniform float modelNightVision;\n"
					+ (hasNightLight ? "varying float vNightLight;\n" : "")
					+ shader.fragmentShader;
				shader.fragmentShader = shader.fragmentShader.replace(
					"#include <color_fragment>",
					[
						"#ifdef USE_COLOR",
						"\tfloat bakedDayLight = vColor.r;",
						"#else",
						"\tfloat bakedDayLight = 1.0;",
						"#endif",
						"\tfloat modelMapBakedLight = bakedDayLight;",
						hasNightLight ? "\tmodelMapBakedLight = mix( vNightLight, bakedDayLight, modelLightBlend );" : "",
						"\tmodelMapBakedLight = mix( modelMapBakedLight, 1.0, modelNightVision );",
						"\tdiffuseColor.rgb *= modelMapBakedLight;"
					].join("\n")
				);
			}.bind(this);
			material.needsUpdate = true;
		},

		_createDisplayMaterial: function(material, geometry) {
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
				emissiveMap: material.emissiveMap || null,
				emissiveIntensity: (typeof material.emissiveIntensity === "number") ? material.emissiveIntensity : 1.0
			});
			displayMaterial.depthWrite = !displayMaterial.transparent;
			if (displayMaterial.map) {
				displayMaterial.map.encoding = THREE.sRGBEncoding;
				this._configureTexture(displayMaterial.map);
			}
			if (displayMaterial.emissiveMap) {
				displayMaterial.emissiveMap.encoding = THREE.sRGBEncoding;
				this._configureTexture(displayMaterial.emissiveMap);
			}
			this._applyBakedLightingShader(displayMaterial, geometry);
			displayMaterial.needsUpdate = true;
			return displayMaterial;
		},

		_prepareSceneForDisplay: function(scene) {
			var me = this;
			scene.traverse(function(node) {
				if (!node.isMesh) {
					return;
				}
				node.frustumCulled = true;
				if (node.geometry) {
					if (!node.geometry.boundingBox) {
						node.geometry.computeBoundingBox();
					}
					if (!node.geometry.boundingSphere) {
						node.geometry.computeBoundingSphere();
					}
				}
				if (node.geometry && !node.geometry.attributes.normal) {
					node.geometry.computeVertexNormals();
					node.geometry.normalizeNormals();
					node.geometry.attributes.normal.needsUpdate = true;
				}
				if ($.isArray(node.material)) {
					node.material = $.map(node.material, function(material) {
						return me._createDisplayMaterial(material, node.geometry);
					});
				}
				else if (node.material) {
					node.material = me._createDisplayMaterial(node.material, node.geometry);
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
					me._pumpTileLoads();
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
				me._pumpTileLoads();
				me._requestRender();
			}, undefined, function() {
				if (me._loadingTiles[tileName] === token) {
					delete me._loadingTiles[tileName];
				}
				me._pumpTileLoads();
			});
		},

		_reloadTile: function(tileName) {
			var tile = this._desiredTiles[tileName] || this._loadedTiles[tileName];
			if (!tile) {
				return;
			}
			delete this._loadingTiles[tileName];
			this._pendingTileLoads = $.grep(this._pendingTileLoads, function(entry) {
				return entry.tileName !== tileName;
			});
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
							if (material.emissiveMap && (material.emissiveMap !== material.map)) {
								material.emissiveMap.dispose();
							}
							material.dispose();
						});
					}
					else {
						if (node.material.map) {
							node.material.map.dispose();
						}
						if (node.material.emissiveMap && (node.material.emissiveMap !== node.material.map)) {
							node.material.emissiveMap.dispose();
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
