/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterInitializer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.GeoServerSecurityManager;
import org.springframework.stereotype.Component;

/** Registers {@link PgStacServiceInfo} companion types with XStream, encrypting the {@code passwd} connection param. */
@Component
public class PgStacXStreamPersisterInitializer implements XStreamPersisterInitializer {

    static final String PASSWORD_KEY = "passwd";

    @Override
    public void init(XStreamPersister persister) {
        XStream xs = persister.getXStream();
        xs.alias("pgstacAccessRule", AccessRule.class);
        xs.allowTypes(new Class[] {AccessRule.class, PgStacServiceInfo.ServiceLevel.class, TimeUnit.class});
        xs.registerLocalConverter(
                PgStacServiceInfoImpl.class, "connectionParameters", new ConnectionParametersConverter());
    }

    /**
     * Flat {@code Map<String, Serializable>} converter, encrypting the {@value #PASSWORD_KEY} entry like store params.
     */
    static class ConnectionParametersConverter implements Converter {

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
            return Map.class.isAssignableFrom(type);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            GeoServerSecurityManager secMgr = GeoServerExtensions.bean(GeoServerSecurityManager.class);
            @SuppressWarnings("unchecked")
            Map<String, Serializable> params = (Map<String, Serializable>) source;
            for (Map.Entry<String, Serializable> entry : params.entrySet()) {
                if (entry.getValue() == null) continue;
                String value = entry.getValue().toString();
                if (PASSWORD_KEY.equals(entry.getKey()) && secMgr != null) {
                    value = secMgr.getConfigPasswordEncryptionHelper().encode(value);
                }
                writer.startNode("entry");
                writer.addAttribute("key", entry.getKey());
                writer.setValue(value);
                writer.endNode();
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            GeoServerSecurityManager secMgr = GeoServerExtensions.bean(GeoServerSecurityManager.class);
            Map<String, Serializable> params = new LinkedHashMap<>();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String key = reader.getAttribute("key");
                String value = reader.getValue();
                if (PASSWORD_KEY.equals(key) && secMgr != null && value != null) {
                    value = secMgr.getConfigPasswordEncryptionHelper().decode(value);
                }
                params.put(key, value);
                reader.moveUp();
            }
            return params;
        }
    }
}
