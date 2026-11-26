<#include "pgstac-common.ftl">
<#global pagetitle = "Items">
<#global pagecrumbs>
  <li class='breadcrumb-item'><a href='${serviceLink("")}'>Home</a></li>
  <li class='breadcrumb-item active'>Items</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">Items</h1>
  <#include "footprints.ftl">
  <table class="table table-sm" id="items">
    <tr><th>Id</th><th>Collection</th><th>Datetime</th></tr>
    <#list model.features as item>
    <tr>
      <td><a href="${serviceLink(itemPath(item.collection, item.id))}">${item.id}</a></td>
      <td><a href="${serviceLink("collections/" + item.collection?url("UTF-8"))}">${item.collection}</a></td>
      <td>${(item.properties.datetime)!}</td>
    </tr>
    <#else>
    <tr><td colspan="3">No items found.</td></tr>
    </#list>
  </table>
  <@paging links=model.links />
<#include "common-footer.ftl">
