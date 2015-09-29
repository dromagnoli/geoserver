package org.geoserver.csw.store.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.geoserver.catalog.CatalogInfo;
import org.geoserver.csw.records.CSWRecordDescriptor;
import org.geotools.feature.AttributeImpl;
import org.geotools.feature.ComplexAttributeImpl;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.ComplexType;

public class RecordCustomizer extends FeatureCustomizer {

    private final static AttributeDescriptor REFERENCES_DESCRIPTOR;

    private final static AttributeDescriptor VALUE_DESCRIPTOR;

    private final static String REFERENCES = "references";

    private final static String TYPENAME = "RecordType";
    static {
        REFERENCES_DESCRIPTOR = CSWRecordDescriptor.getDescriptor(REFERENCES);
        ComplexType referenceType = (ComplexType) REFERENCES_DESCRIPTOR.getType();
        VALUE_DESCRIPTOR = (AttributeDescriptor) referenceType.getDescriptor("value");

    }

    /** An instance of DownloadLinkGenerator, used to create download links */
    private DownloadLinkGenerator linkGenerator;

    public void setLinkGenerator(DownloadLinkGenerator linkGenerator) {
        this.linkGenerator = linkGenerator;
    }

    public RecordCustomizer() {
        super(TYPENAME);
    }

    @Override
    public void customizeFeature(Feature feature, CatalogInfo resource) {
        Iterator<String> links = linkGenerator.generateDownloadLinks(resource);
        Collection<Property> newReferencesList = new ArrayList<Property>();
        while (links.hasNext()) {
            String link = links.next();

            Property urlAttribute = new AttributeImpl(link, VALUE_DESCRIPTOR, null);

            // Setting references
            Property references = new ComplexAttributeImpl(Collections.singletonList(urlAttribute),
                    REFERENCES_DESCRIPTOR, null);

            newReferencesList.add(references);
        }
        Collection<Property> propertyList = new ArrayList<Property>();
        Collection<Property> oldValues = (Collection<Property>) feature.getValue();
        Iterator<Property> oldValuesIterator = oldValues.iterator();
        boolean insertReferences = false;

        // Copy all previous elements, references included
        while (oldValuesIterator.hasNext()) {
            Property prop = oldValuesIterator.next();
            if (REFERENCES.equalsIgnoreCase(prop.getName().getLocalPart())) {
                insertReferences = true;
            } else if (insertReferences) {
                // append new references to the current collection
                // before going to other elements
                propertyList.addAll(newReferencesList);
                insertReferences = false;
            }
            propertyList.add(prop);
        }
        feature.setValue(propertyList);
    }
}