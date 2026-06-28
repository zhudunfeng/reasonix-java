package com.reansonix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reasonix 总配置属性
 */
@Component
@ConfigurationProperties(prefix = "reasonix")
public class ReasonixConfig {

    private String defaultModel;
    private int maxSteps;
    private double temperature;
    private boolean autoPlan;
    private double compactRatio;
    private String plannerModel;
    private ProviderProperties provider = new ProviderProperties();
    private Tools tools = new Tools();
    private Skills skills = new Skills();
    private Permissions permissions = new Permissions();
    private Serve serve = new Serve();
    private Subagent subagent = new Subagent();

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public boolean isAutoPlan() { return autoPlan; }
    public void setAutoPlan(boolean autoPlan) { this.autoPlan = autoPlan; }
    public double getCompactRatio() { return compactRatio; }
    public void setCompactRatio(double compactRatio) { this.compactRatio = compactRatio; }
    public String getPlannerModel() { return plannerModel; }
    public void setPlannerModel(String plannerModel) { this.plannerModel = plannerModel; }
    public ProviderProperties getProvider() { return provider; }
    public void setProvider(ProviderProperties provider) { this.provider = provider; }
    public Tools getTools() { return tools; }
    public void setTools(Tools tools) { this.tools = tools; }
    public Skills getSkills() { return skills; }
    public void setSkills(Skills skills) { this.skills = skills; }
    public Permissions getPermissions() { return permissions; }
    public void setPermissions(Permissions permissions) { this.permissions = permissions; }
    public Serve getServe() { return serve; }
    public void setServe(Serve serve) { this.serve = serve; }
    public Subagent getSubagent() { return subagent; }
    public void setSubagent(Subagent subagent) { this.subagent = subagent; }

    public static class ProviderProperties {
        private String defaultModel;
        private String timeout;
        private List<SupplierDef> suppliers = new ArrayList<>();
        private List<ModelDef> models = new ArrayList<>();

        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        public List<SupplierDef> getSuppliers() { return suppliers; }
        public void setSuppliers(List<SupplierDef> suppliers) { this.suppliers = suppliers; }
        public List<ModelDef> getModels() { return models; }
        public void setModels(List<ModelDef> models) { this.models = models; }

        public static class SupplierDef {
            private String id;
            private String providerType;
            private String baseUrl;
            private String apiKey;
            private boolean enabled;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getProviderType() { return providerType; }
            public void setProviderType(String providerType) { this.providerType = providerType; }
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }

        public static class ModelDef {
            private String id;
            private String supplierId;
            private String modelName;
            private boolean stream;
            private boolean enabled;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getSupplierId() { return supplierId; }
            public void setSupplierId(String supplierId) { this.supplierId = supplierId; }
            public String getModelName() { return modelName; }
            public void setModelName(String modelName) { this.modelName = modelName; }
            public boolean isStream() { return stream; }
            public void setStream(boolean stream) { this.stream = stream; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    public static class Tools {
        private List<String> enabled = new ArrayList<>();
        private long bashTimeoutSeconds;

        public List<String> getEnabled() { return enabled; }
        public void setEnabled(List<String> enabled) { this.enabled = enabled; }
        public long getBashTimeoutSeconds() { return bashTimeoutSeconds; }
        public void setBashTimeoutSeconds(long bashTimeoutSeconds) { this.bashTimeoutSeconds = bashTimeoutSeconds; }
    }

    public static class Skills {
        private List<String> paths = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();

        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public List<String> getDisabled() { return disabled; }
        public void setDisabled(List<String> disabled) { this.disabled = disabled; }
    }

    public static class Permissions {
        private String mode = "ask";
        private List<String> deny = new ArrayList<>();
        private List<String> allow = new ArrayList<>();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public List<String> getDeny() { return deny; }
        public void setDeny(List<String> deny) { this.deny = deny; }
        public List<String> getAllow() { return allow; }
        public void setAllow(List<String> allow) { this.allow = allow; }
    }

    public static class Serve {
        private String authMode = "none";
        private int port = 8080;

        public String getAuthMode() { return authMode; }
        public void setAuthMode(String authMode) { this.authMode = authMode; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class Subagent {
        private int maxParallel = 2;
        private List<AgentDef> agents = new ArrayList<>();

        public int getMaxParallel() { return maxParallel; }
        public void setMaxParallel(int maxParallel) { this.maxParallel = maxParallel; }
        public List<AgentDef> getAgents() { return agents; }
        public void setAgents(List<AgentDef> agents) { this.agents = agents; }

        public static class AgentDef {
            private String name;
            private String description;
            private List<String> tools = new ArrayList<>();
            private String workspaceMode;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
            public List<String> getTools() { return tools; }
            public void setTools(List<String> tools) { this.tools = tools; }
            public String getWorkspaceMode() { return workspaceMode; }
            public void setWorkspaceMode(String workspaceMode) { this.workspaceMode = workspaceMode; }
        }
    }
}
