<#include "pgstac-common.ftl">
<#global pagetitle = model.title!"Schema">
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item active'>${model.title!"Schema"}</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">${model.title!"Schema"}</h1>
  <p class="my-4">${model["$id"]!}</p>
  <table class="table table-sm" id="properties">
    <tr><th>Name</th><th>Type</th><th>Description</th></tr>
    <#list model.properties as name, definition>
    <tr><td>${name}</td><td><@value v=(definition.type!definition["$ref"]!"") /><#if definition.format??> (${definition.format})</#if></td><td>${definition.title!} ${definition.description!}</td></tr>
    <#else>
    <tr><td colspan="3">No properties.</td></tr>
    </#list>
  </table>
<#include "common-footer.ftl">
