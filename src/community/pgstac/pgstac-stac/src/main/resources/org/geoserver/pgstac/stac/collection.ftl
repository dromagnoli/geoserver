<#include "pgstac-common.ftl">
<#assign path = "collections/" + model.id?url("UTF-8")>
<#global pagetitle = model.title!model.id>
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item'><a href='${serviceLink("collections")}'>Collections</a></li>
  <li class='breadcrumb-item active'>${model.id}</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">${model.title!model.id}</h1>
  <p class="my-4">${model.description!}</p>
  <ul id="browse">
    <li><a href="${serviceLink(path + "/items")}">Items</a></li>
    <li><a href="${serviceLink(path + "/queryables")}">Queryables</a> and <a href="${serviceLink(path + "/sortables")}">sortables</a></li>
  </ul>

  <table class="table table-sm" id="details">
    <tr><th>Id</th><td>${model.id}</td></tr>
    <tr><th>License</th><td>${model.license!}</td></tr>
    <#if model.keywords??><tr><th>Keywords</th><td><@value v=model.keywords /></td></tr></#if>
    <#if model.extent??><tr><th>Extent</th><td><@value v=model.extent /></td></tr></#if>
    <#if model.providers??><tr><th>Providers</th><td><@value v=model.providers /></td></tr></#if>
    <#if model.summaries??><tr><th>Summaries</th><td><@value v=model.summaries /></td></tr></#if>
    <#if model.assets??><tr><th>Assets</th><td><@value v=model.assets /></td></tr></#if>
  </table>
<#include "common-footer.ftl">
