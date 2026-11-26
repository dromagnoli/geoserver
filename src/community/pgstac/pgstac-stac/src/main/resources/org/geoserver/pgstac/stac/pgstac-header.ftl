<#-- The ogcapi-core header, without the format chooser: it calls methods the JSON maps of this service do not have -->
<#setting locale="en_US">
<#setting time_zone="utc">
<!DOCTYPE html>
<html lang="en">
  <head>
      <title>${pagetitle!"STAC API"}</title>
      <meta http-equiv="content-type" content="text/html; charset=${.output_encoding}">
      <link rel="stylesheet" href="${resourceLink("apicss/bootstrap.min.css")}" type="text/css" media="all" />
      <link rel="stylesheet" href="${resourceLink("css/geoserver.css")}" type="text/css" media="all" />
      <link rel="stylesheet" href="${resourceLink("apicss/ogcapi.css")}" type="text/css" media="all" />
      <link rel="stylesheet" href="${resourceLink("apicss/treeview.css")}" type="text/css" media="all" />
      <script src="${resourceLink('webresources/ogcapi/common.js')}"></script>
  </head>
<body>
  <header id="header" class="gs-header">
    <div class="gs-header-bar">
      <div class="gs-header-left">
        <a class="logo" href="${serviceLink("")}"></a>
      </div>
    </div>
  </header>
  <#if pagecrumbs??>
  <div id="breadcrumb" class="py-2 mb-4">
    <div class="container">
      <nav aria-label="breadcrumb">
        <ol class="breadcrumb mb-0">
          ${pagecrumbs}
        </ol>
      </nav>
    </div>
  </div>
  </#if>
  <main>
    <div id="content" class="container">
