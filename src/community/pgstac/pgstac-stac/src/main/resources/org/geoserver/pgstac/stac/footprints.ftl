<#-- Map of the footprints in modelJSON, the item or page of items of the request -->
<link rel="stylesheet" href="${resourceLink("openlayers10/ol.css")}" type="text/css">
<div id="footprints" class="my-3" style="height: 400px"></div>
<script id="footprints-data" type="application/json">${modelJSON?no_esc}</script>
<script src="${resourceLink("openlayers10/ol.js")}"></script>
<script src="${resourceLink("webresources/pgstac/footprints.js")}"></script>
