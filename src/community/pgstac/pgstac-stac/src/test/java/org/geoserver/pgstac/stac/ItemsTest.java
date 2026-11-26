/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import java.util.List;
import org.geoserver.pgstac.AccessRule;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.junit.Test;

/** Checks the items endpoints: paging, single items, links and hidden collections. */
public class ItemsTest extends PgStacApiTestSupport {

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "%s", "assets": {},
             "links": [{"rel": "self", "href": "/stored/self.json"}, {"rel": "via", "href": "https://example.com/source"}],
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-0%dT00:00:00Z"},
             "_geoserver": {"location": "/data/%1$s.tif"}}
            """;

    @Override
    protected void configure(PgStacServiceInfo info) {
        AccessRule rule = new AccessRule();
        // id is an ECQL keyword (feature id filters), so the property must be quoted
        rule.setCqlFilter("\"id\" = 'hidden'");
        rule.getRoles().add("ROLE_HIDDEN");
        info.getCollectionAccessRules().add(rule);
    }

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        for (String collection : List.of("open", "hidden")) facade.createCollection(json(COLLECTION, collection));
        facade.createItem(json(ITEM, "i1", "open", 1));
        facade.createItem(json(ITEM, "i2", "open", 2));
        facade.createItem(json(ITEM, "i3", "open", 3));
        facade.createItem(json(ITEM, "h1", "hidden", 1));
    }

    @Test
    public void testItemsPaging() throws Exception {
        // PgSTAC default order: datetime descending
        DocumentContext first = getAsJSONPath("ogc/stac/v1/collections/open/items?limit=2", 200);
        assertThat(first.read("features[*].id"), contains("i3", "i2"));
        List<String> next = first.read("links[?(@.rel == 'next')].href");
        assertThat(next.get(0), startsWith(BASE + "collections/open/items?limit=2&token=next"));

        DocumentContext second = getAsJSONPath(next.get(0).substring(ROOT.length()), 200);
        assertThat(second.read("features[*].id"), contains("i1"));
        assertThat(second.read("links[?(@.rel == 'next')]"), empty());
        List<String> prev = second.read("links[?(@.rel == 'prev')].href");
        assertThat(
                getAsJSONPath(prev.get(0).substring(ROOT.length()), 200).read("features[*].id"), contains("i3", "i2"));
    }

    @Test
    public void testItem() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/collections/open/items/i1", 200);
        assertEquals("i1", json.read("id"));
        // the stored self link is replaced, the other stored links are kept
        assertThat(json.read("links[?(@.rel == 'self')].href"), contains(BASE + "collections/open/items/i1"));
        assertThat(json.read("links[*].rel"), containsInAnyOrder("via", "self", "parent", "collection", "root"));
        assertThat(json.read("$.keys()"), not(hasItem("_geoserver")));
    }

    @Test
    public void testItemNotFound() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/collections/open/items/missing", 404);
        assertEquals("NotFound", json.read("code"));
        assertEquals("Item not found: missing", json.read("description"));
    }

    @Test
    public void testHiddenCollection() throws Exception {
        getAsJSONPath("ogc/stac/v1/collections/hidden/items", 404);
        getAsJSONPath("ogc/stac/v1/collections/hidden/items/h1", 404);
        getAsJSONPath("ogc/stac/v1/collections/missing/items", 404);
    }
}
