<#include "pgstac-common.ftl">
<#global pagetitle = "Collections">
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item active'>Collections</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">Collections</h1>
  <div class="row" id="collections">
    <#list model.collections as collection>
    <div class="col-xs-12 col-md-6 col-lg-4 pb-4">
      <div class="card h-100">
        <div class="card-header">
          <h2><a href="${serviceLink("collections/" + collection.id?url("UTF-8"))}">${collection.title!collection.id}</a></h2>
        </div>
        <div class="card-body">${collection.description!}</div>
      </div>
    </div>
    <#else>
    <div class="p-3 mb-2 bg-warning text-dark">No collections found.</div>
    </#list>
  </div>
  <@paging links=model.links />
<#include "common-footer.ftl">
