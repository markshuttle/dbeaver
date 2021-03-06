/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.dashboard.registry;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.ui.dashboard.internal.UIDashboardActivator;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.xml.XMLBuilder;
import org.jkiss.utils.xml.XMLException;
import org.jkiss.utils.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardRegistry {
    private static final Log log = Log.getLog(DashboardRegistry.class);

    private static DashboardRegistry instance = null;

    public synchronized static DashboardRegistry getInstance() {
        if (instance == null) {
            instance = new DashboardRegistry(Platform.getExtensionRegistry());
        }
        return instance;
    }

    private final Map<String, DashboardDescriptor> dashboardList = new LinkedHashMap<>();
    private final List<DashboardTypeDescriptor> dashboardTypeList = new ArrayList<>();

    private DashboardRegistry(IExtensionRegistry registry) {
        // Load data dashboardList from external plugins
        IConfigurationElement[] extElements = registry.getConfigurationElementsFor(DashboardDescriptor.EXTENSION_ID);
        // Load types
        for (IConfigurationElement ext : extElements) {
            if ("dashboardType".equals(ext.getName())) {
                dashboardTypeList.add(
                    new DashboardTypeDescriptor(ext));
            }
        }
        // Load dashboards from extensions
        for (IConfigurationElement ext : extElements) {
            if ("dashboard".equals(ext.getName())) {
                DashboardDescriptor dashboard = new DashboardDescriptor(this, ext);
                dashboardList.put(dashboard.getId(), dashboard);
            }
        }

        // Load dashboards from config
        File configFile = getDashboardsConfigFile();
        if (configFile.exists()) {
            try {
                loadConfigFromFile(configFile);
            } catch (Exception e) {
                log.error("Error loading dashboard configuration", e);
            }
        }
    }

    private void loadConfigFromFile(File configFile) throws XMLException {
        Document dbDocument = XMLUtils.parseDocument(configFile);
        for (Element dbElement : XMLUtils.getChildElementList(dbDocument.getDocumentElement(), "dashboard")) {
            DashboardDescriptor dashboard = new DashboardDescriptor(this, dbElement);
            dashboardList.put(dashboard.getId(), dashboard);
        }
    }

    private void saveConfigFile() {
        try (OutputStream out = new FileOutputStream(getDashboardsConfigFile())){
            XMLBuilder xml = new XMLBuilder(out, GeneralUtils.UTF8_ENCODING);
            xml.startElement("dashboards");
            for (DashboardDescriptor dashboard : dashboardList.values()) {
                if (dashboard.isCustom()) {
                    xml.startElement("dashboard");
                    dashboard.serialize(xml);
                    xml.endElement();
                }
            }
            xml.endElement();
            out.flush();
        } catch (Exception e) {
            log.error("Error saving dashboard configuration", e);
        }
    }

    private File getDashboardsConfigFile() {
        return new File(UIDashboardActivator.getDefault().getStateLocation().toFile(), "dashboards.xml");
    }

    public DashboardTypeDescriptor getDashboardType(String id) {
        for (DashboardTypeDescriptor descriptor : dashboardTypeList) {
            if (descriptor.getId().equals(id)) {
                return descriptor;
            }
        }
        return null;
    }

    public List<DashboardDescriptor> getAllDashboards() {
        return new ArrayList<>(dashboardList.values());
    }

    public DashboardDescriptor getDashboards(String id) {
        return dashboardList.get(id);
    }

    public List<DashboardDescriptor> getDashboards(DBPDataSourceContainer dataSourceContainer, boolean defaultOnly) {
        List<DashboardDescriptor> result = new ArrayList<>();
        for (DashboardDescriptor dd : dashboardList.values()) {
            if (dd.matches(dataSourceContainer)) {
                if (!defaultOnly || dd.isShowByDefault()) {
                    result.add(dd);
                }
            }
        }
        return result;
    }

    public void createDashboard(DashboardDescriptor dashboard) throws IllegalArgumentException {
        if (dashboardList.containsKey(dashboard.getId())) {
            throw new IllegalArgumentException("Dashboard " + dashboard.getId() + "' already exists");
        }
        if (!dashboard.isCustom()) {
            throw new IllegalArgumentException("Only custom dashboards can be added");
        }
        dashboardList.put(dashboard.getId(), dashboard);

        saveConfigFile();
    }

    public void removeDashboard(DashboardDescriptor dashboard) throws IllegalArgumentException {
        if (!dashboardList.containsKey(dashboard.getId())) {
            throw new IllegalArgumentException("Dashboard " + dashboard.getId() + "' doesn't exist");
        }
        if (!dashboard.isCustom()) {
            throw new IllegalArgumentException("Only custom dashboards can be removed");
        }
        dashboardList.remove(dashboard.getId());

        saveConfigFile();
    }

}
