/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store.raster;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.web.data.store.StoreEditPanel;
import org.geoserver.web.data.store.panel.CogFileParamPanel;
import org.geoserver.web.data.store.panel.FileModel;
import org.geoserver.web.wicket.FileExistsValidator;
import org.geoserver.web.wicket.browser.ExtensionFileFilter;
import org.geotools.util.logging.Logging;


public class CogRasterEditPanel extends StoreEditPanel {

    private final static String COG_SCHEMA = "cog";

    private final static String COG_PREFIX = COG_SCHEMA + ":";

    public CogRasterEditPanel(String componentId, Form storeEditForm, String... fileExtensions) {
        super(componentId, storeEditForm);

        final IModel model = storeEditForm.getModel();
        setDefaultModel(model);
        CogFileParamPanel file =
                new CogFileParamPanel(
                        "url",
                        new PropertyModel(model, "URL"),
                        new ResourceModel("url", "URL"),
                        true);

        if (fileExtensions != null && fileExtensions.length > 0) {
            file.setFileFilter(new Model(new ExtensionFileFilter(fileExtensions)));
        }

        file.getFormComponent().add(new CombinedCogFileExistsValidator());
        add(file);
    }

    static class CombinedCogFileExistsValidator implements IValidator<String> {

        // Inner FileExistsValidator being used when the input has no cog prefix
        FileExistsValidator fileExistsValidator = new FileExistsValidator();

        @Override
        public void validate(IValidatable<String> validatable) {
            String uriSpec = validatable.getValue();

            // Check if it's a cog path or a standard file
            try {
                URI uri = new URI(uriSpec);
                if (uri.getScheme() != null && COG_SCHEMA.equals(uri.getScheme())) {

                } else {
                    fileExistsValidator.validate(validatable);
                }
            } catch (URISyntaxException e) {
                // may be a windows path, move on
            }
        }
    }

    public static class CogModel implements IModel<String> {

        static final Logger LOGGER =
                Logging.getLogger(org.geoserver.web.data.store.panel.FileModel.class);

        FileModel fileModel;
        IModel<String> delegate;

        public CogModel(IModel<String> delegate) {
            this.delegate = delegate;
            this.fileModel =
                    new FileModel(
                            delegate,
                            GeoServerExtensions.bean(GeoServerResourceLoader.class)
                                    .getBaseDirectory());
        }

        @Override
        public String getObject() {
            Object obj = delegate.getObject();
            if (obj instanceof URL) {
                URL url = (URL) obj;
                return url.toExternalForm();
            }
            return (String) obj;
        }

        public void setObject(String location) {
            if (location != null) {
                if (!location.startsWith(COG_PREFIX)) {
                    fileModel.setObject(location);
                    location = fileModel.getObject();
                }
            }
            delegate.setObject(location);
        }

        @Override
        public void detach() {}
    }
}
