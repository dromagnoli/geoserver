package org.geoserver.csw.store.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;

import org.geoserver.catalog.CatalogInfo;
import org.geoserver.csw.records.iso.MetaDataDescriptor;
import org.geotools.feature.AttributeImpl;
import org.geotools.feature.ComplexAttributeImpl;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.expression.PropertyName;

public class MetadataCustomizer extends FeatureCustomizer {

    private static final String TYPENAME = "MD_Metadata_Type";

    private final static String ONLINE_PARENT_NODE = "gmd:distributionInfo/gmd:MD_Distribution/gmd:transferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine";

    private final static AttributeDescriptor LINKAGE_ATTRIBUTE_DESCRIPTOR;

    private final static AttributeDescriptor LINKAGE_URL_ATTRIBUTE_DESCRIPTOR;

    private final static AttributeDescriptor ONLINE_RESOURCE_DESCRIPTOR;

    static {
        ComplexType distrPropType = (ComplexType) ((FeatureType) MetaDataDescriptor.METADATA_DESCRIPTOR
                .getType()).getDescriptor("distributionInfo").getType();
        ComplexType distrType = (ComplexType) distrPropType.getDescriptor("MD_Distribution")
                .getType();
        ComplexType transferOptionsPropType = (ComplexType) distrType.getDescriptor(
                "transferOptions").getType();
        ComplexType transferOptionsType = (ComplexType) transferOptionsPropType.getDescriptor(
                "MD_DigitalTransferOptions").getType();
        AttributeDescriptor onlinePropTypeDescr = (AttributeDescriptor) transferOptionsType
                .getDescriptor("onLine");
        ComplexType onlinePropType = (ComplexType) onlinePropTypeDescr.getType();
        ONLINE_RESOURCE_DESCRIPTOR = (AttributeDescriptor) onlinePropType
                .getDescriptor("CI_OnlineResource");
        ComplexType onlineType = (ComplexType) ONLINE_RESOURCE_DESCRIPTOR.getType();
        LINKAGE_ATTRIBUTE_DESCRIPTOR = (AttributeDescriptor) onlineType.getDescriptor("linkage");
        ComplexType urlPropType = (ComplexType) LINKAGE_ATTRIBUTE_DESCRIPTOR.getType();
        LINKAGE_URL_ATTRIBUTE_DESCRIPTOR = (AttributeDescriptor) urlPropType.getDescriptor("URL");
    }

    /** An instance of DownloadLinkGenerator, used to create download links */
    private DownloadLinkGenerator linkGenerator;

    public DownloadLinkGenerator getLinkGenerator() {
        return linkGenerator;
    }

    public void setLinkGenerator(DownloadLinkGenerator linkGenerator) {
        this.linkGenerator = linkGenerator;
    }

    public MetadataCustomizer() {
        super(TYPENAME);
    }

    public void customizeFeature(Feature feature, CatalogInfo resource) {
        PropertyName parentPropertyName = ff.property(ONLINE_PARENT_NODE,
                MetaDataDescriptor.NAMESPACES);
        Property parentProperty = (Property) parentPropertyName.evaluate(feature);
        if (parentProperty == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Unable to get the specified property for the current feature: "
                        + ONLINE_PARENT_NODE + "\n No customization will be applied");
            }
            return;
        }

        // Getting feature values to be updated
        Object value = parentProperty.getValue();
        if (value == null || !(value instanceof Collection)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Unable to get a value for the current property: "
                        + parentPropertyName.getPropertyName()
                        + "\n No customization will be applied");
            }
            return;
        }
        Collection<ComplexAttribute> onlineValues = (Collection<ComplexAttribute>) value;

        // Copy the collection due to the immutable return
        Collection<ComplexAttribute> updatedOnlineResources = new ArrayList<ComplexAttribute>(
                (Collection<ComplexAttribute>) onlineValues);

        // Invoke the DownloadLinkGenerator to generate links for the specified resource
        Iterator<String> links = linkGenerator.generateDownloadLinks(resource);
        while (links.hasNext()) {
            String link = links.next();
            // Setting new URL attribute
            Property urlAttribute = new AttributeImpl(link, LINKAGE_URL_ATTRIBUTE_DESCRIPTOR, null);

            // Setting linkageURL
            Property linkage = new ComplexAttributeImpl(Collections.singletonList(urlAttribute),
                    LINKAGE_ATTRIBUTE_DESCRIPTOR, null);

            ComplexAttribute onlineResourceElement = new ComplexAttributeImpl(
                    Collections.singletonList(linkage), ONLINE_RESOURCE_DESCRIPTOR, null);

            // Add the newly created element
            updatedOnlineResources.add(onlineResourceElement);
        }

        // Update the onlineResources
        parentProperty.setValue(updatedOnlineResources);

    }

}