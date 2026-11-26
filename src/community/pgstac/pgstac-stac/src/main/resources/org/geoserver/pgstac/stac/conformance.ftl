<#global pagetitle = "Conformance">
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item active'>Conformance</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">Conformance</h1>
  <p class="my-4">The conformance classes implemented by this service.</p>
  <ul id="conformance">
  <#list model.conformsTo as uri>
    <li><a href="${uri}" target="_blank">${uri}</a></li>
  </#list>
  </ul>
<#include "common-footer.ftl">
