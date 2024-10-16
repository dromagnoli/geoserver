/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.mapml.gwc.gridset;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geoserver.gwc.GWC;
import org.geoserver.mapml.tcrs.Bounds;
import org.geoserver.mapml.tcrs.Point;
import org.geoserver.mapml.tcrs.TiledCRS;
import org.geoserver.mapml.tcrs.TiledCRSConstants;
import org.geoserver.mapml.tcrs.TiledCRSParams;
import org.geotools.ows.wmts.model.TileMatrix;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.SimpleGridSetConfiguration;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.springframework.beans.factory.annotation.Autowired;

/** @author prushforth */
public class MapMLGridsets extends SimpleGridSetConfiguration {

    public static class GridSetLevelType {
        boolean numeric = true;

        boolean prefixed;

        String prefix;

        public boolean isNumeric() {
            return numeric;
        }

        public boolean isPrefixed() {
            return prefixed;
        }

        public String getPrefix() {
            return prefix;
        }

        @Override
        public String toString() {
            return "GridSetLevelType{"
                    + "numeric="
                    + numeric
                    + ", prefixed="
                    + prefixed
                    + ", prefix='"
                    + prefix
                    + '\''
                    + '}';
        }
    }

    private static final Logger log = Logging.getLogger(MapMLGridsets.class);

    public static final List<String> FIXED_NAMES =
            Arrays.asList("APSTILE", "CBMTILE", "OSMTILE", "WGS84");

    private final GridSet WGS84;
    private final GridSet OSMTILE;
    private final GridSet CBMTILE;
    private final GridSet APSTILE;
    @Autowired private static GWC gwc = GWC.get();

    /** */
    public MapMLGridsets() {
        log.fine("Adding MapML WGS84 gridset");
        WGS84 =
                GridSetFactory.createGridSet(
                        "WGS84",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        true,
                        GridSetFactory.DEFAULT_LEVELS,
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        256,
                        256,
                        false);
        WGS84.setDescription("World Geodetic System 1984");
        for (int i = 0; i < GridSetFactory.DEFAULT_LEVELS; i++) {
            WGS84.getGrid(i).setName(Integer.toString(i));
        }
        addInternal(WGS84);

        log.fine("Adding MapML OSMTILE gridset");
        OSMTILE =
                GridSetFactory.createGridSet(
                        "OSMTILE",
                        SRS.getEPSG3857(),
                        BoundingBox.WORLD3857,
                        true,
                        OSMTILEResolutions(),
                        null,
                        1.0D,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        integerLevelNames(OSMTILEResolutions().length),
                        256,
                        256,
                        false);
        OSMTILE.setDescription(
                "Web Mercator-based tiled coordinate reference system. "
                        + "Applied by many global map applications, "
                        + "for areas excluding polar latitudes.");
        addInternal(OSMTILE);

        log.fine("Adding MapML CBMTILE gridset");
        Bounds cb = TiledCRSConstants.tiledCRSDefinitions.get("CBMTILE").getBounds();
        BoundingBox cb_bbox =
                new BoundingBox(
                        cb.getMin().getX(),
                        cb.getMin().getY(),
                        cb.getMax().getX(),
                        cb.getMax().getY());

        CBMTILE =
                GridSetFactory.createGridSet(
                        "CBMTILE",
                        SRS.getSRS(3978),
                        cb_bbox,
                        true,
                        CBMTILEResolutions(),
                        null,
                        1.0D,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        integerLevelNames(CBMTILEResolutions().length),
                        256,
                        256,
                        false);
        CBMTILE.setDescription(
                "Lambert Conformal Conic-based tiled " + "coordinate reference system for Canada.");
        addInternal(CBMTILE);

        log.fine("Adding MapML APSTILE gridset");
        Bounds at_b = TiledCRSConstants.tiledCRSDefinitions.get("APSTILE").getBounds();
        BoundingBox at_bbox =
                new BoundingBox(
                        at_b.getMin().getX(),
                        at_b.getMin().getY(),
                        at_b.getMax().getX(),
                        at_b.getMax().getY());

        APSTILE =
                GridSetFactory.createGridSet(
                        "APSTILE",
                        SRS.getSRS(5936),
                        at_bbox,
                        true,
                        APSTILEResolutions(),
                        null,
                        1.0D,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        integerLevelNames(APSTILEResolutions().length),
                        256,
                        256,
                        false);

        APSTILE.setDescription(
                "Alaska Polar Stereographic-based tiled "
                        + "coordinate reference system for the Arctic region.");
        addInternal(APSTILE);
        getGridSets().stream()
                .forEach(
                        g -> {
                            if (!gwc.getGridSetBroker().getGridSetNames().contains(g.getName())) {
                                try {
                                    gwc.getGridSetBroker().addGridSet(g);
                                } catch (UnsupportedOperationException ioe) {
                                    log.log(
                                            Level.SEVERE,
                                            "Error occurred adding gridset: '" + g.getName() + "'",
                                            ioe);
                                }
                            }
                            // embedded gridsets aren't editable by the user,
                            // which is what we want, so push this onto that list
                            // needs to be added to list first time and every time
                            // we start up, because it's not a "default" gridset.
                            gwc.addEmbeddedGridSet(g.getName());
                        });
        gwc.getConfig()
                .setDefaultCachingGridSetIds(
                        getGridSets().stream().map(g -> g.getName()).collect(toSet()));
        try {
            gwc.saveConfig(gwc.getConfig());
            // Trigger the TCRS loading
            TiledCRSConstants.reloadDefinitions();
        } catch (IOException ioe) {
            log.log(Level.INFO, "Error occured saving MapMLGridsets config.", ioe);
        }
    }

    /** @return array of resolutions m/px */
    private double[] CBMTILEResolutions() {
        double[] CBMTILEResolutions = {
            38364.660062653464D,
            22489.62831258996D,
            13229.193125052918D,
            7937.5158750317505D,
            4630.2175937685215D,
            2645.8386250105837D,
            1587.5031750063501D,
            926.0435187537042D,
            529.1677250021168D,
            317.50063500127004D,
            185.20870375074085D,
            111.12522225044451D,
            66.1459656252646D,
            38.36466006265346D,
            22.48962831258996D,
            13.229193125052918D,
            7.9375158750317505D,
            4.6302175937685215D,
            2.6458386250105836D,
            1.5875031750063502D,
            0.92604351875370428D,
            0.52916772500211673D,
            0.31750063500127002D,
            0.18520870375074083D,
            0.11112522225044451D,
            0.066145965625264591D
        };
        return CBMTILEResolutions;
    }
    /**
     * @param resolutions
     * @return array of string integer representations of zoom levels
     */
    private String[] integerLevelNames(int resolutions) {
        String[] names = new String[resolutions];
        for (int i = 0; i < resolutions; i++) {
            names[i] = Integer.toString(i);
        }
        return names;
    }

    /** @return array of resolutions m/px */
    private double[] APSTILEResolutions() {
        double[] APSTILEResolutions = {
            238810.813354D,
            119405.406677D,
            59702.7033384999D,
            29851.3516692501D,
            14925.675834625D,
            7462.83791731252D,
            3731.41895865639D,
            1865.70947932806D,
            932.854739664032D,
            466.427369832148D,
            233.213684916074D,
            116.606842458037D,
            58.3034212288862D,
            29.1517106145754D,
            14.5758553072877D,
            7.28792765351156D,
            3.64396382688807D,
            1.82198191331174D,
            0.910990956788164D,
            0.45549547826179D
        };
        return APSTILEResolutions;
    }

    /** @return array of resolutions m/px */
    private double[] OSMTILEResolutions() {
        double[] OSMTILEResolutions = {
            156543.03390625D,
            78271.516953125D,
            39135.7584765625D,
            19567.87923828125D,
            9783.939619140625D,
            4891.9698095703125D,
            2445.9849047851562D,
            1222.9924523925781D,
            611.4962261962891D,
            305.74811309814453D,
            152.87405654907226D,
            76.43702827453613D,
            38.218514137268066D,
            19.109257068634033D,
            9.554628534317017D,
            4.777314267158508D,
            2.388657133579254D,
            1.194328566789627D,
            0.5971642833948135D
            //            0.2985821416974068D, these are not defined in the spec
            //            0.1492910708487034D,
            //            0.0746455354243517D,
            //            0.0373227677121758D,
            //            0.0186613838560879D,
            //            0.009330691928044D,
            //            0.004665345964022D,
            //            0.002332672982011D,
            //            0.0011663364910055D,
            //            0.0005831682455027D,
            //            0.0002915841227514D,
            //            0.0001457920613757D
        };
        return OSMTILEResolutions;
    }

    /** @throws GeoWebCacheException */
    @Override
    public void afterPropertiesSet() throws GeoWebCacheException {}

    /** @return */
    @Override
    public String getIdentifier() {
        return "DefaultGridsets";
    }

    /** @return */
    @Override
    public String getLocation() {
        return "Default";
    }

    /**
     * Returns a list of GridSet names that share a common prefix based on getPrefix method.
     * Prioritized names are put at the top and others are sorted alphabetically.
     *
     * @param gridSets The collection of GridSets to process.
     * @return A list of GridSet names filtered and sorted as required.
     */
    public List<String> getCandidateGridSets() {
        // Filter GridSets by common prefix using getPrefix method

        // We can consider the result being cached unless a GWC change occurred
        List<String> filteredNames =
                gwc.getGridSetBroker().getGridSets().stream()
                        .filter(gridSet -> canBeSupportedAsTiledCRS(gridSet))
                        .map(GridSet::getName) // Map to the name of the GridSet
                        .collect(Collectors.toList());

        Collections.sort(filteredNames);
        return filteredNames;
    }

    public static boolean canBeSupportedAsTiledCRS(GridSet gridSet) {
        String name = gridSet.getName();
        if (FIXED_NAMES.contains(name) || name.contains(":")) {
            return false;
        }
        GridSetLevelType levelType = MapMLGridsets.getLevelType(getLevelNamesFromGridSet(gridSet));
        return levelType.isNumeric() || levelType.isPrefixed();
    }

    public static Map<String, TiledCRSParams> getTiledCRSs(List<String> gridSetNames) {
        GridSetBroker broker = gwc.getGridSetBroker();
        Map<String, TiledCRSParams> map = new HashMap<>();
        for (String gridsetName : gridSetNames) {
            GridSet grid = broker.get(gridsetName);
            if (grid != null) {
                TiledCRS tiledCRS = getMapMLTiledCRS(grid);
                map.put(tiledCRS.getName(), tiledCRS.getParams());
            } else {
                log.warning("Requested gridset doesn't exist. Skipping it: " + gridsetName);
            }
        }
        return map;
    }

    public static TiledCRS getMapMLTiledCRS(GridSet gridSet) {
        String crsName = gridSet.getName();
        String projection = gridSet.getSrs().toString(); // CRS as a string (e.g., "EPSG:3857")
        int tileWidth = gridSet.getTileWidth();
        BoundingBox bbox = gridSet.getBounds();
        Bounds bounds =
                new Bounds(
                        new Point(bbox.getMinX(), bbox.getMinY()),
                        new Point(bbox.getMaxX(), bbox.getMaxY()));
        Point origin = new Point(bounds.getMin().getX(), bounds.getMax().getY());

        // Map resolutions to zoom levels
        int levels = gridSet.getNumLevels();

        double[] scales = new double[levels];

        for (int i = 0; i < levels; i++) {
            Grid grid = gridSet.getGrid(i);
            scales[i] = 1d / grid.getResolution();
        }
        TiledCRSParams tiledCRSParams =
                new TiledCRSParams(crsName, projection, bounds, tileWidth, origin, scales);
        return new TiledCRS(crsName, tiledCRSParams);
    }

    public static List<String> getLevelNamesFromGridSet(GridSet gridSet) {
        List<String> levelNames = new ArrayList<>();
        for (int i = 0; i < gridSet.getNumLevels(); i++) {
            Grid grid = gridSet.getGrid(i);
            levelNames.add(grid.getName());
        }

        return levelNames;
    }

    public static List<String> getLevelNamesFromTileMatrixList(List<TileMatrix> tileMatrices) {
        List<String> levelNames = new ArrayList<>();

        // Iterate over each TileMatrix and add its identifier to the list
        for (TileMatrix tileMatrix : tileMatrices) {
            levelNames.add(tileMatrix.getIdentifier().toString());
        }

        return levelNames;
    }

    public static GridSetLevelType getLevelType(List<String> levels) {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        // First check: levels are simple numbers:
        GridSetLevelType levelType = new GridSetLevelType();
        String numericPattern = "-?\\d+(\\.\\d+)?";
        for (String level : levels) {
            // Check if the level name matches the numeric pattern
            if (!level.matches(numericPattern)) {
                levelType.numeric = false;
                break;
            }
        }
        if (levelType.numeric) {
            return levelType;
        }
        // Second check: levels having a common prefix, e.g.:
        // EPSG:4326:0
        // EPSG:4326:1
        // EPSG:4326:2
        // EPSG:4326:3

        // Since TileMatrix is a {z} level in MapML client, we will
        // prefix the value with the common prefix if available

        // Start with the first level as the prefix candidate
        String prefix = levels.get(0);

        // Iterate over the rest of the levels and trim the prefix
        for (int i = 1; i < levels.size(); i++) {
            while (levels.get(i).indexOf(prefix) != 0) {
                // Trim the last character from the prefix until it matches
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    levelType.prefixed = false;
                    return levelType; // No common prefix found
                }
            }
        }

        // Check if the remaining prefix is actually a valid common prefix (not just a number)
        if (prefix.matches("\\d+")) {
            return null; // A prefix consisting of only numbers is not valid
        }
        levelType.prefix = prefix;
        levelType.prefixed = true;
        return levelType;
    }
}
