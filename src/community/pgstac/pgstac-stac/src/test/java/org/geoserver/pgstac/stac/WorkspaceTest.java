/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.pgstac.GeoServerMetadata;
import org.geoserver.pgstac.PgStacFacade;
import org.junit.Test;
import tools.jackson.databind.node.ObjectNode;

/** Checks a workspace URL shows only the collections of that workspace and the global ones, with links kept in it. */
public class WorkspaceTest extends PgStacApiTestSupport {

    private static final String SF = ROOT + "sf/ogc/stac/v1/";

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "%s", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-15T00:00:00Z"}}
            """;

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        addWorkspace("sf");
        addWorkspace("cite");
        facade.createCollection(collection("global", null));
        facade.createCollection(collection("sf-only", "sf"));
        facade.createCollection(collection("cite-only", "cite"));
        facade.createItem(json(ITEM, "g1", "global"));
        facade.createItem(json(ITEM, "s1", "sf-only"));
        facade.createItem(json(ITEM, "c1", "cite-only"));
    }

    private void addWorkspace(String name) {
        Catalog catalog = getCatalog();
        // the security test data already has some of the default workspaces
        if (catalog.getWorkspaceByName(name) != null) return;
        WorkspaceInfo workspace = catalog.getFactory().createWorkspace();
        workspace.setName(name);
        catalog.add(workspace);
        NamespaceInfo namespace = catalog.getFactory().createNamespace();
        namespace.setPrefix(name);
        namespace.setURI("http://www.example.com/" + name);
        catalog.add(namespace);
    }

    private static ObjectNode collection(String id, String workspace) {
        ObjectNode collection = json(COLLECTION, id);
        if (workspace != null)
            collection.putObject(GeoServerMetadata.KEY).putArray("workspaces").add(workspace);
        return collection;
    }

    @Test
    public void testGlobalServiceSeesAll() throws Exception {
        assertThat(ids("ogc/stac/v1/collections", "collections"), containsInAnyOrder("global", "sf-only", "cite-only"));
    }

    @Test
    public void testWorkspaceCollections() throws Exception {
        assertThat(ids("sf/ogc/stac/v1/collections", "collections"), containsInAnyOrder("global", "sf-only"));
        assertThat(ids("sf/ogc/stac/v1/search", "features"), containsInAnyOrder("g1", "s1"));
        assertThat(ids("sf/ogc/stac/v1/search?collections=cite-only", "features"), empty());
    }

    @Test
    public void testOtherWorkspaceNotFound() throws Exception {
        for (String path :
                List.of("collections/cite-only", "collections/cite-only/items", "collections/cite-only/items/c1")) {
            getAsJSONPath("sf/ogc/stac/v1/" + path, 404);
        }
    }

    @Test
    public void testLinksKeepWorkspace() throws Exception {
        List<String> self =
                getAsJSONPath("sf/ogc/stac/v1/collections/sf-only", 200).read("links[?(@.rel == 'self')].href");
        assertThat(self, contains(SF + "collections/sf-only"));
        List<String> items = getAsJSONPath("sf/ogc/stac/v1/collections/sf-only/items", 200)
                .read("features[0].links[?(@.rel == 'self')].href");
        assertThat(items, contains(SF + "collections/sf-only/items/s1"));
    }

    private List<String> ids(String path, String member) throws Exception {
        return getAsJSONPath(path, 200).read(member + "[*].id");
    }
}
