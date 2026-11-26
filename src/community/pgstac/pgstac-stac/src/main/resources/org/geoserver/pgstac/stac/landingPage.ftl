<#global pagetitle = model.title!"STAC API">
<#global pagecrumbs>
  <li class='breadcrumb-item active'>Home</li>
</#global>
<#include "pgstac-header.ftl">

  <h1 id="title">${model.title!"STAC API"}</h1>
  <p class="my-4">${model.description!}</p>

  <ul id="links">
    <li><a href="${serviceLink("collections")}">Collections</a>: the collections of this catalog</li>
    <li><a href="${serviceLink("search")}">Search</a>: the items of all collections</li>
    <li><a href="${serviceLink("queryables")}">Queryables</a> and <a href="${serviceLink("sortables")}">sortables</a>: the properties usable in filters and sorting</li>
    <li><a href="${serviceLink("openapi")}">API definition</a>, in OpenAPI 3</li>
    <li><a href="${serviceLink("conformance")}">Conformance</a>: the standards this service implements</li>
  </ul>

  <h2>Contact information</h2>
  <address>
    <ul>
      <li>Server managed by ${contact.contactPerson!"-unspecified-"}</li>
      <li>Organization: ${contact.contactOrganization!"-unspecified-"}</li>
      <li>Mail: <a href="mailto:${contact.contactEmail!''}">${contact.contactEmail!"-unspecified-"}</a></li>
    </ul>
  </address>
<#include "common-footer.ftl">
