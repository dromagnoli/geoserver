/* (c) 2022 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.libdeflate;

import it.geosolutions.imageio.compression.libdeflate.LibDeflateCompressorSpi;
import java.io.Serializable;

/** Basic Libdeflate Plugin Settings */
public class LibdeflateSettings implements Serializable {

    public LibdeflateSettings() {}

    public LibdeflateSettings(LibdeflateSettings settings) {
        this.compressorPriority = settings.compressorPriority;
        this.decompressorPriority = settings.decompressorPriority;
        this.minLevel = settings.minLevel;
        this.maxLevel = settings.maxLevel;
    }

    public static final String LIBDEFLATE_SETTINGS_KEY = "LibdeflateSettings.Key";

    protected int compressorPriority = LibDeflateCompressorSpi.getDefaultPriority();

    protected int decompressorPriority = LibDeflateCompressorSpi.getDefaultPriority();

    protected int minLevel = LibDeflateCompressorSpi.getDefaultMinLevel();

    public int getCompressorPriority() {
        return compressorPriority;
    }

    public void setCompressorPriority(int compressorPriority) {
        this.compressorPriority = compressorPriority;
    }

    public int getDecompressorPriority() {
        return decompressorPriority;
    }

    public void setDecompressorPriority(int decompressorPriority) {
        this.decompressorPriority = decompressorPriority;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    protected int maxLevel = LibDeflateCompressorSpi.getDefaultMaxLevel();
}
