package org.geoserver.mapml.tcrs;

import org.geoserver.mapml.xml.ProjType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

public class WrappingProjType {

    boolean isBuiltIn;

    ProjType projType;

    String projection;

    public WrappingProjType(String proj) throws FactoryException {
        for (ProjType v: ProjType.values()) {
                if (v.name().equalsIgnoreCase(proj.toUpperCase())) {
                        projType = v;
                        isBuiltIn = true;
                        break;
                    }
            }
        int epsg = getEpsgCode(proj);
        for (ProjType c : ProjType.values()) {
            if (c.epsgCode == (epsg)) {
                projType = c;
                isBuiltIn = true;
                break;
            }
        }

        if (!isBuiltIn) {
                projection = proj;
            }
        if (false /*!isSupported*/) {
                throw new IllegalArgumentException("Unsupported Proj");
            }
    }

    private static int getEpsgCode(String codeWithPrefix) throws FactoryException {
        CoordinateReferenceSystem coordinateReferenceSystem = CRS.decode(codeWithPrefix, false);
        return CRS.lookupEpsgCode(coordinateReferenceSystem, true);
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public String getProjection() {
        return projection;
    }


    public String value() {
        return isBuiltIn ? projType.value() : projection;
    }

    public ProjType unwrap() {
        return projType;
    }

    public TiledCRS getTiledCRS() {
        return TiledCRSConstants.lookupTCRS(value());
    }

    public String getCRSCode() {
        TiledCRSParams tcrs = TiledCRSConstants.lookupTCRSParams(value());
        return tcrs.getCode();
    }

    public CoordinateReferenceSystem getCRS() {
        return getTiledCRS().getCRS();
    }
}
