<#include "pgstac-common.ftl">
<#global pagetitle = model.id>
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item'><a href='${serviceLink("collections")}'>Collections</a></li>
  <li class='breadcrumb-item'><a href='${serviceLink("collections/" + model.collection?url("UTF-8"))}'>${model.collection}</a></li>
  <li class='breadcrumb-item active'>${model.id}</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">${model.id}</h1>
  <#include "footprints.ftl">
  <h2>Properties</h2>
  <table class="table table-sm" id="properties">
    <#list (model.properties)!{} as name, v>
    <tr><th>${name}</th><td><@value v=(v!"") /></td></tr>
    </#list>
  </table>
  <h2>Assets</h2>
  <table class="table table-sm" id="assets">
    <#list (model.assets)!{} as key, asset>
    <tr><th>${key}</th><td><a href="${asset.href}">${asset.title!asset.href}</a> ${asset.type!}</td></tr>
    </#list>
  </table>
<#include "common-footer.ftl">
