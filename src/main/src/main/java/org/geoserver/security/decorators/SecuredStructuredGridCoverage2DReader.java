/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.decorators;

import java.io.IOException;
import java.util.List;
import org.geoserver.security.AccessLevel;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.Response;
import org.geoserver.security.SecureCatalogImpl;
import org.geoserver.security.WrapperPolicy;
import org.geotools.api.coverage.grid.Format;
import org.geotools.api.data.ResourceInfo;
import org.geotools.api.data.ServiceInfo;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GranuleStore;
import org.geotools.coverage.grid.io.HarvestedSource;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.util.factory.Hints;

/**
 * Applies access limits policies around the wrapped reader
 *
 * @author Daniele Romagnoli - GeoSolutions
 */
public class SecuredStructuredGridCoverage2DReader extends DecoratingStructuredGridCoverage2DReader {

    WrapperPolicy policy;

    public SecuredStructuredGridCoverage2DReader(StructuredGridCoverage2DReader delegate, WrapperPolicy policy) {
        super(delegate);
        this.policy = policy;
    }

    @Override
    public Format getFormat() {
        Format format = delegate.getFormat();
        if (format == null) {
            return null;
        } else {
            return SecuredObjects.secure(format, policy);
        }
    }

    @Override
    public GridCoverage2D read(GeneralParameterValue... parameters) throws IllegalArgumentException, IOException {
        return SecuredGridCoverage2DReader.read(delegate, policy, null, parameters);
    }

    @Override
    public GridCoverage2D read(String coverageName, GeneralParameterValue... parameters)
            throws IllegalArgumentException, IOException {
        return SecuredGridCoverage2DReader.read(delegate, policy, coverageName, parameters);
    }

    @Override
    public ServiceInfo getInfo() {
        ServiceInfo info = delegate.getInfo();
        if (info == null) {
            return null;
        } else {
            return SecuredObjects.secure(info, policy);
        }
    }

    @Override
    public ResourceInfo getInfo(String coverageName) {
        ResourceInfo info = delegate.getInfo(coverageName);
        if (info == null) {
            return null;
        } else {
            return SecuredObjects.secure(info, policy);
        }
    }

    /**
     * Wraps the granule source so that the {@link CoverageAccessLimits#getReadFilter() read filter} restricts granule
     * enumeration, and scopes removals and updates to the visible granules when write access is granted. The returned
     * source is never a {@link GranuleStore} for a read only request, whatever the delegate hands back.
     *
     * @return null if the delegate has no such coverage
     */
    @Override
    public GranuleSource getGranules(String coverageName, boolean readOnly)
            throws IOException, UnsupportedOperationException {
        // a read restriction is not a write restriction, so the mutable store is denied on the access level alone
        if (!readOnly) {
            checkWriteAccess(coverageName);
        }
        Filter readFilter = readFilter();
        GranuleSource granules = delegate.getGranules(coverageName, readOnly);
        // readers report an unknown coverage with a null source, wrapping it would only delay the failure
        if (granules == null) {
            return null;
        }
        if (!readOnly && granules instanceof GranuleStore) {
            return readFilter == null ? granules : new SecuredGranuleStore((GranuleStore) granules, readFilter);
        }
        // nothing forbids a read only request from returning a store, hide the write methods it would hand over
        if (readFilter == null) {
            return granules instanceof GranuleStore ? new SecuredGranuleSource(granules, Filter.INCLUDE) : granules;
        }
        return new SecuredGranuleSource(granules, readFilter);
    }

    /** Reports the reader as read only whenever the policy withholds write access, whatever the delegate says. */
    @Override
    public boolean isReadOnly() {
        return policy.getAccessLevel() != AccessLevel.READ_WRITE || delegate.isReadOnly();
    }

    @Override
    public void createCoverage(String coverageName, SimpleFeatureType schema)
            throws IOException, UnsupportedOperationException {
        checkWriteAccess(coverageName);
        delegate.createCoverage(coverageName, schema);
    }

    @Override
    public boolean removeCoverage(String coverageName, boolean delete)
            throws IOException, UnsupportedOperationException {
        checkWriteAccess(coverageName);
        return delegate.removeCoverage(coverageName, delete);
    }

    @Override
    public void delete(boolean deleteData) throws IOException {
        checkWriteAccess(null);
        delegate.delete(deleteData);
    }

    /**
     * Checks write access only: harvested granules are not matched against the read filter, so a caller with write
     * access can index granules its own read limits will then hide from it. One call can also target more than one
     * coverage, {@code defaultTargetCoverage} being only the fallback.
     */
    @Override
    public List<HarvestedSource> harvest(String defaultTargetCoverage, Object source, Hints hints)
            throws IOException, UnsupportedOperationException {
        checkWriteAccess(defaultTargetCoverage);
        // no read filter check, unlike addGranules: HarvestedSource does not report which granules were indexed, and
        // deleting them afterwards would run the removal policy, which can erase the files themselves
        return delegate.harvest(defaultTargetCoverage, source, hints);
    }

    /** The granule restriction to apply, {@code null} when the policy does not restrict granules at all. */
    private Filter readFilter() {
        if (!(policy.getLimits() instanceof CoverageAccessLimits)) {
            return null;
        }
        Filter readFilter = ((CoverageAccessLimits) policy.getLimits()).getReadFilter();
        return readFilter == null || Filter.INCLUDE.equals(readFilter) ? null : readFilter;
    }

    /**
     * Denies the operation unless the policy grants write access, using a Spring security exception when the policy
     * asks for a challenge, so the user gets a chance to authenticate.
     *
     * @param coverageName the target coverage, {@code null} when the operation targets the whole reader
     */
    private void checkWriteAccess(String coverageName) throws IOException {
        if (policy.getAccessLevel() == AccessLevel.READ_WRITE) {
            return;
        }
        // the name is only needed to report the failure, do not pay for it on the allowed path
        String name = coverageName != null ? coverageName : firstCoverageName();
        if (policy.getResponse() == Response.CHALLENGE) {
            throw SecureCatalogImpl.unauthorizedAccess(name);
        }
        throw new UnsupportedOperationException(name + " does not allow write access");
    }

    /** Name to report when the failing operation targets the whole reader rather than a single coverage. */
    private String firstCoverageName() throws IOException {
        String[] names = delegate.getGridCoverageNames();
        return names == null || names.length == 0 ? "coverage" : names[0];
    }
}
