/* Original by Eerik Mägi (Pisi-Deff @ GitHub)
 * https://gist.github.com/Pisi-Deff/92e00008f809383c82d926f843b6dd0f
*/

componentconstructors['regiongrid'] = function(dynmap, configuration) {
	// var cfg = $.extend({}, configuration);

	var me = this;
	
	if(configuration.grids){
		this.grids = [];
		for(var i = 0; i < configuration.grids.length; i++) {
			var cfgGrid = configuration.grids[i];
			var newGrid = makeSet(cfgGrid.sizeInChunks*16, cfgGrid.weight, cfgGrid.color, cfgGrid.title);
			this.grids.push(newGrid);
		}
	}
	else{
		this.grids = [
			makeSet(16, '1', '#800000', 'Grid - Chunks'),
			makeSet(16*32, '3', '#000080', 'Grid - Regions'),
		];
	}
	
	dynmap.map.on('moveend', function () {
		setTimeout(function () {updateGrid();}, 0);
	});

	dynmap.map.on('layeradd layerremove', function (event) {
		for(var i = 0; i < me.grids.length; i++){
			if (event.layer === me.grids[i].group)
			{
				updateGrid();
				break;
			}
		}
	});

	function updateGrid() {
		var atLeastOne = false;
		for(var i = 0; i < me.grids.length; i++){
			removeMarkers(me.grids[i].markers, me.grids[i].group);
			if(dynmap.map.hasLayer(me.grids[i].group))
				atLeastOne = true;
		}


		if (!atLeastOne){
			return;
		}

		var minMax = getMinMaxData();

		for(var i = 0; i < me.grids.length; i++){
			addAreas(me.grids[i].group, me.grids[i].markers, me.grids[i].style, me.grids[i].sizeInBlocks, minMax.minBlock, minMax.maxBlock);
		}
		
	}

	function removeMarkers(markers, group) {
		while (markers.length) {
			group.removeLayer(markers.pop());
		}
	}

	function addAreas(group, markers, style, multiplier, min, max) {
		if (!dynmap.map.hasLayer(group)) return;

		var minx = Math.floor(min.x / multiplier) - 50;
		var maxx = Math.ceil(max.x / multiplier) + 50;
		var minz = Math.floor(min.z / multiplier) - 50;
		var maxz = Math.ceil(max.z / multiplier) + 50;
		
		if (Math.max(maxx - minx, maxz - minz) > 500) return; // for performance reasons, plus it's not useful at this stage
		
		for (var x = minx; x < maxx; x++) {
			var pointList = [
				latlng(x * multiplier, 64, minz * multiplier),
				latlng(x * multiplier, 64, maxz * multiplier)
			];
			var marker = new L.Polyline(pointList, style);
			markers.push(marker);
			group.addLayer(marker);
		}
		for (var z = minz; z < maxz; z++) {
			var pointList = [
				latlng(minx * multiplier, 64, z * multiplier),
				latlng(maxx * multiplier, 64, z * multiplier)
			];
			var marker = new L.Polyline(pointList, style);
			markers.push(marker);
			group.addLayer(marker);
		}
	}

	function getMinMaxData() {
		// 0,0 => chunk 0 0 => region 0 0
		// 15, 15 => chunk 0 0 => region 0 0
		// 16, 16 => chunk 1 1 => region 0 0
		// 12228, 8898 => chunk 764 556 => region 23 17

		var bounds = dynmap.map.getBounds(),
			projection = dynmap.maptype.getProjection();

		var ne = projection.fromLatLngToLocation(bounds.getNorthEast(), 64),
			se = projection.fromLatLngToLocation(bounds.getSouthEast(), 64),
			sw = projection.fromLatLngToLocation(bounds.getSouthWest(), 64),
			nw = projection.fromLatLngToLocation(bounds.getNorthWest(), 64);

		var minX = Math.min(ne.x, se.x, sw.x, nw.x),
			maxX = Math.max(ne.x, se.x, sw.x, nw.x),
			minZ = Math.min(ne.z, se.z, sw.z, nw.z),
			maxZ = Math.max(ne.z, se.z, sw.z, nw.z),

			minBlock = {
				x: minX,
				z: minZ
			},
			maxBlock = {
				x: maxX,
				z: maxZ
			};

		return {
			minBlock: minBlock,
			maxBlock: maxBlock
		};
	}
	function makeSet(sizeInBlocks, weight, color, title){
		var group = new L.LayerGroup();
		var markers = [];
		var style = {
			color: color,
			weight: weight,
			opacity: 0.6,
			clickable: false,
			fill: false
		};
		dynmap.addToLayerSelector(group, title, 1000 + sizeInBlocks);
		
		return {
			group: group,
			markers: markers,
			style: style,
			sizeInBlocks: sizeInBlocks
		};
	}
};
