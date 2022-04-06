/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.libdeflate.LibdeflateSettings;

/** Basic Panel to configure LibdeflateSettings. */
public class LibdeflateSettingsPanel<T extends LibdeflateSettings> extends FormComponentPanel<T> {

    protected final WebMarkupContainer container;
    private final TextField<Integer> compressionPriority;
    private final TextField<Integer> decompressionPriority;
    private final TextField<Integer> minLevel;
    private final TextField<Integer> maxLevel;

    public LibdeflateSettingsPanel(String id, IModel<T> model) {
        super(id, model);

        container = new WebMarkupContainer("container");
        container.setOutputMarkupId(true);
        add(container);

        compressionPriority =
                new TextField<>(
                        "compressionPriority", new PropertyModel(model, "compressionPriority"));
        decompressionPriority = new TextField<>("decompressionPriority");
        minLevel = new TextField<>("minLevel");
        maxLevel = new TextField<>("maxLevel");
        compressionPriority.add(
                new OnChangeAjaxBehavior() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        int priority = compressionPriority.getModelObject().intValue();
                        LibdeflateSettings object = getSettings(model);
                        object.setCompressorPriority(priority);
                        model.setObject((T) object);
                    }
                });
        decompressionPriority.add(
                new OnChangeAjaxBehavior() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        int priority = decompressionPriority.getModelObject().intValue();
                        LibdeflateSettings object = getSettings(model);
                        object.setDecompressorPriority(priority);
                        model.setObject((T) object);
                    }
                });
        minLevel.add(
                new OnChangeAjaxBehavior() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        int priority = minLevel.getModelObject().intValue();
                        LibdeflateSettings object = getSettings(model);
                        object.setMinLevel(priority);
                        model.setObject((T) object);
                    }
                });
        maxLevel.add(
                new OnChangeAjaxBehavior() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        int priority = maxLevel.getModelObject().intValue();
                        LibdeflateSettings object = getSettings(model);
                        object.setMaxLevel(priority);
                        model.setObject((T) object);
                    }
                });
    }

    private LibdeflateSettings getSettings(IModel<T> model) {
        LibdeflateSettings settings = model.getObject();
        if (settings == null) {
            model.setObject((T) new LibdeflateSettings());
        }
        return settings;
    }
}
