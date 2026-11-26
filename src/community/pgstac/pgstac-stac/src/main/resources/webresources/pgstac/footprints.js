/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
// Draws the GeoJSON of the page in lon/lat without wrapping the world, so a footprint split at the
// antimeridian shows as its two parts at the map edges. A graticule only, no basemap: no outside requests.
(function () {
  var data = JSON.parse(document.getElementById('footprints-data').textContent);
  var source = new ol.source.Vector({features: new ol.format.GeoJSON().readFeatures(data)});
  var map = new ol.Map({
    target: 'footprints',
    layers: [new ol.layer.Graticule({showLabels: true, wrapX: false}), new ol.layer.Vector({source: source})],
    view: new ol.View({projection: 'EPSG:4326', center: [0, 0], zoom: 1, extent: [-180, -90, 180, 90]})
  });
  if (source.getFeatures().length > 0) {
    map.getView().fit(source.getExtent(), {padding: [20, 20, 20, 20], maxZoom: 10});
  }
})();
