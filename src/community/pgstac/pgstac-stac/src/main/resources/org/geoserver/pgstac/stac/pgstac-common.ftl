<#-- Prints any JSON value: scalars as text, arrays inline, objects as nested lists. Strings, numbers and
     booleans are also hashes for the bean wrapper, so they are tested first -->
<#macro value v>
<#if v?is_boolean || v?is_number>${v?c}<#elseif v?is_string>${v}<#elseif v?is_sequence>[<#list v as e><@value v=(e!"") /><#sep>, </#list>]<#elseif v?is_hash_ex><ul><#list v as k, e><li><b>${k}</b>: <@value v=(e!"") /></li></#list></ul></#if><#t>
</#macro>

<#-- Paging links of a page of collections or items -->
<#macro paging links>
  <#list links?filter(l -> l.rel == "prev" || l.rel == "next")>
  <nav class="my-3" id="paging">
    <#items as link><a class="btn btn-outline-primary btn-sm me-2" href="${link.href}">${link.rel?cap_first}</a></#items>
  </nav>
  </#list>
</#macro>

<#-- Path of an item page, with the ids encoded as path segments -->
<#function itemPath collection id>
  <#return "collections/" + collection?url("UTF-8") + "/items/" + id?url("UTF-8")>
</#function>
