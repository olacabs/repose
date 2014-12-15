package org.openrepose.nodeservice.jmx;

import com.google.common.base.Optional;
import org.openrepose.commons.config.manager.UpdateListener;
import org.openrepose.commons.config.resource.ConfigurationResource;
import org.openrepose.commons.utils.digest.impl.SHA1MessageDigester;
import org.openrepose.core.filter.SystemModelInterrogator;
import org.openrepose.core.spring.ReposeSpringProperties;
import org.openrepose.core.systemmodel.Filter;
import org.openrepose.core.systemmodel.ReposeCluster;
import org.openrepose.core.systemmodel.SystemModel;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.core.services.healthcheck.HealthCheckService;
import org.openrepose.core.services.healthcheck.HealthCheckServiceProxy;
import org.openrepose.core.services.healthcheck.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.util.*;

@Named("reposeConfigurationInformation")
@ManagedResource(objectName = "org.openrepose.nodeservice.jmx:type=ConfigurationInformation", description = "Repose configuration information MBean.")
public class ConfigurationInformation implements ConfigurationInformationMBean {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationInformation.class);
    private static final String FILTER_EXCEPTION_MESSAGE = "Error updating Mbean for Filter";
    public static final String SYSTEM_MODEL_CONFIG_HEALTH_REPORT = "SystemModelConfigError";

    private final ConfigurationService configurationService;
    private final List<FilterInformation> filterChain;

    private HealthCheckServiceProxy healthCheckServiceProxy;
    private SystemModelListener systemModelListener;
    private String clusterId;
    private String nodeId;

    public static class FilterInformation {
        private final String id;
        private final String name;
        private final String regex;
        private final String configuration;
        private boolean isConfiguarationLoaded;
        private Map successConfigurationLoadinginformation;
        private Map failedConfigurationLoadingInformation;

        public FilterInformation(String id, String name, String regex, String configuration, Boolean isConfiguarationLoaded) {
            this.id = id;
            this.name = name;
            this.regex = regex;
            this.configuration = configuration;
            this.isConfiguarationLoaded = isConfiguarationLoaded;
            successConfigurationLoadinginformation = new HashMap<String, String[]>();
            failedConfigurationLoadingInformation = new HashMap<String, String[]>();


        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getRegex() {
            return regex;
        }

        public String getConfiguration() {
            return configuration;
        }

        public Boolean getIsConfiguarationLoaded() {
            return isConfiguarationLoaded;
        }

        public void setConfiguarationLoaded(boolean isConfiguarationLoaded) {
            this.isConfiguarationLoaded = isConfiguarationLoaded;
        }

        public Map<String, String[]> getSuccessConfigurationLoadinginformation() {
            return successConfigurationLoadinginformation;
        }

        public void setSuccessConfigurationLoadinginformation(Map successConfigurationLoadinginformation) {
            this.successConfigurationLoadinginformation = successConfigurationLoadinginformation;
        }

        public Map<String, String[]> getFailedConfigurationLoadingInformation() {
            return failedConfigurationLoadingInformation;
        }

        public void setFailedConfigurationLoadingInformation(Map failedConfigurationLoadingInformation) {
            this.failedConfigurationLoadingInformation = failedConfigurationLoadingInformation;
        }
    }

    private class SystemModelListener implements UpdateListener<SystemModel> {

        private boolean initialized = false;

        @Override
        public void configurationUpdated(SystemModel systemModel) {
            LOG.info("System model updated");
            initialized = false;

            SystemModelInterrogator interrogator = new SystemModelInterrogator(clusterId, nodeId);
            Optional<ReposeCluster> cluster = interrogator.getLocalCluster(systemModel);

            if (cluster.isPresent()) {
                synchronized (filterChain) {
                    filterChain.clear();

                    if (cluster.get().getFilters() != null && cluster.get().getFilters().getFilter() != null) {
                        for (Filter filter : cluster.get().getFilters().getFilter()) {
                            filterChain.add(new FilterInformation(filter.getId(), filter.getName(), filter.getUriRegex(),
                                    filter.getConfiguration(), false));
                        }
                    }
                }

                initialized = true;

                healthCheckServiceProxy.resolveIssue(SYSTEM_MODEL_CONFIG_HEALTH_REPORT);
            } else {
                LOG.error("Unable to identify the local host in the system model - please check your system-model.cfg.xml");
                healthCheckServiceProxy.reportIssue(SYSTEM_MODEL_CONFIG_HEALTH_REPORT, "Unable to identify the " +
                        "local host in the system model - please check your system-model.cfg.xml", Severity.BROKEN);
            }
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }
    }

    @Inject
    public ConfigurationInformation(
            ConfigurationService configurationService,
            @Value(ReposeSpringProperties.NODE.CLUSTER_ID)String clusterId,
            @Value(ReposeSpringProperties.NODE.NODE_ID)String nodeId,
            HealthCheckService healthCheckService) {
        this.clusterId = clusterId;
        this.nodeId = nodeId;
        filterChain = new ArrayList<>();
        this.configurationService = configurationService;
        this.healthCheckServiceProxy = healthCheckService.register();
    }

    @Override
    @ManagedOperation
    public List<CompositeData> getFilterChain() throws OpenDataException {
        List<CompositeData> list = new ArrayList<>();
        synchronized (filterChain) {
            for (FilterInformation filter : filterChain) {
                list.add(new ConfigurationInformationCompositeDataBuilder(filter).toCompositeData());
            }
        }

        return list;
    }

    @PostConstruct
    public void init() {
        systemModelListener = new SystemModelListener();
        configurationService.subscribeTo("system-model.cfg.xml", systemModelListener, SystemModel.class);
    }

    @PreDestroy
    public void destroy() {
        configurationService.unsubscribeFrom("system-model.cfg.xml", systemModelListener);
        systemModelListener = null;
    }

    public void setFilterLoadingInformation(String filterName, boolean filterInitialized, ConfigurationResource configurationResource) {
        synchronized (filterChain) {
            for (FilterInformation filter : filterChain) {
                if (filterName.equalsIgnoreCase(filter.getName())) {
                    filter.setConfiguarationLoaded(filterInitialized);
                    try {
                        GregorianCalendar gcal = new GregorianCalendar();
                        XMLGregorianCalendar xgcal = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);

                        try {
                            if (configurationResource != null) {


                                filter.successConfigurationLoadinginformation.put(configurationResource.name(), new String[]{xgcal.toString(), byteArrayToHexString(new SHA1MessageDigester().digestStream(configurationResource.newInputStream()))});
                                if (filter.failedConfigurationLoadingInformation.containsKey(configurationResource.name())) {
                                    filter.failedConfigurationLoadingInformation.remove(configurationResource.name());
                                } else if (configurationService.getResourceResolver().resolve(filter.getConfiguration()).name().equalsIgnoreCase(configurationResource.name())) {
                                    filter.failedConfigurationLoadingInformation.clear();
                                }


                            }

                        } catch (IOException e) {
                            filter.failedConfigurationLoadingInformation.put(configurationResource.name(), new String[]{xgcal.toString(), "", e.getMessage()});
                            LOG.debug(FILTER_EXCEPTION_MESSAGE, e);

                        }
                    } catch (Exception e) {
                        LOG.debug(FILTER_EXCEPTION_MESSAGE, e);
                    }
                }
            }
        }
    }

    public void setFilterLoadingFailedInformation(String filterName, ConfigurationResource configurationResource, String errorInformation) {

        synchronized (filterChain) {
            for (FilterInformation filter : filterChain) {
                if (filterName.equalsIgnoreCase(filter.getName())) {
                    try {
                        GregorianCalendar gcal = new GregorianCalendar();
                        XMLGregorianCalendar xgcal = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
                        try {
                            xgcal.getTimezone();

                            if (configurationResource != null) {
                                if (filter.failedConfigurationLoadingInformation.containsKey(configurationResource.name())) {
                                    filter.failedConfigurationLoadingInformation.remove(configurationResource.name());
                                }
                                filter.failedConfigurationLoadingInformation.put(configurationResource.name(), new String[]{xgcal.toString(), byteArrayToHexString(new SHA1MessageDigester().digestStream(configurationResource.newInputStream())), errorInformation});

                            }

                        } catch (IOException e) {
                            filter.failedConfigurationLoadingInformation.put(configurationResource.name(), new String[]{xgcal.toString(), "", e.getMessage()});
                            LOG.debug(FILTER_EXCEPTION_MESSAGE, e);

                        }
                    } catch (Exception e) {
                        LOG.debug(FILTER_EXCEPTION_MESSAGE, e);
                    }
                }
            }
        }
    }

    public static String byteArrayToHexString(byte[] b) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            builder.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
        }
        return builder.toString();
    }
}