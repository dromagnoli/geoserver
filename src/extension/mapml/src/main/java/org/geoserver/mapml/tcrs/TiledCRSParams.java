/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.mapml.tcrs;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** @author prushforth */
public class TiledCRSParams {

    private final String name;
    private final String code;
    private final Bounds bounds;
    private final int TILE_SIZE;
    private final double[] scales;
    private final double[] resolutions;
    private final Point origin;

    /**
     * @param name
     * @param code
     * @param bounds
     * @param tileSize
     * @param origin
     * @param scales
     */
    public TiledCRSParams(
            String name, String code, Bounds bounds, int tileSize, Point origin, double[] scales) {
        this.name = name;
        this.code = code;
        this.bounds = bounds;
        this.TILE_SIZE = tileSize;
        this.origin = origin;
        this.scales = scales;
        // although they are redundant since one is the inverse of the other
        // we store both of them for quick access (e.g. debug) without need
        // to recompute them
        this.resolutions = new double[scales.length];
        for (int i=0; i<scales.length; i++) {
            resolutions[i] = 1d/scales[i];
        }
    }

    /** @return */
    public String getName() {
        return name;
    }

    /** @return */
    public String getCode() {
        return code;
    }

    /** @return */
    public Bounds getBounds() {
        return bounds;
    }

    /** @return */
    public int getTILE_SIZE() {
        return TILE_SIZE;
    }

    /** @return */
    public double[] getScales() {
        return scales;
    }

    /** @return */
    public Point getOrigin() {
        return origin;
    }

    /**
     * Returns the MapML full CRS name for these params
     *
     * @return
     */
    public String getSRSName() {
        return TiledCRSFactory.AUTHORITY + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TiledCRSParams that = (TiledCRSParams) o;
        return TILE_SIZE == that.TILE_SIZE
                && Objects.equals(name, that.name)
                && Objects.equals(code, that.code)
                && Objects.equals(bounds, that.bounds)
                && Objects.deepEquals(scales, that.scales)
                && Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, code, bounds, TILE_SIZE, Arrays.hashCode(scales), origin);
    }

    public String buildDefinition(int indentChars) {
        String indent = " ".repeat(indentChars);
        String originString = String.format("[%.8f, %.8f]", origin.getX(), origin.getY());

        // Convert resolutions array to string
        StringBuilder resolutionsString = new StringBuilder("[");
        for (int i = 0; i < resolutions.length; i++) {
            resolutionsString.append(resolutions[i]);
            if (i != resolutions.length - 1) {
                resolutionsString.append(", ");
            }
        }
        resolutionsString.append("]");

        // Convert bounds to JSON-like array
        String boundsString = String.format(Locale.ENGLISH, "[[%.8f, %.8f], [%.8f, %.8f]]",
                bounds.getMin().getX(), bounds.getMin().getY(),
                bounds.getMax().getX(), bounds.getMax().getY());

        // Create the JSON-like structure with proper indentation
        String result =
                indent + "\"projection\": \"" + name + "\",\n" +
                indent + "\"origin\": " + originString + ",\n" +
                indent + "\"resolutions\": " + resolutionsString + ",\n" +
                indent + "\"bounds\": " + boundsString + ",\n" +
                indent + "\"tilesize\": " + TILE_SIZE;
        return result;
    }
}
