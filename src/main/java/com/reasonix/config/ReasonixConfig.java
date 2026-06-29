package com.reasonix.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reasonix 总配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "reasonix")
public class ReasonixConfig {

    private String defaultModel;
    private int maxSteps = 50;
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

    @Data
    public static class ProviderProperties {
        private String defaultModel;
        private String timeout;
        private List<SupplierDef> suppliers = new ArrayList<>();
        private List<ModelDef> models = new ArrayList<>();

        @Data
        public static class SupplierDef {
            private String id;
            private String providerType;
            private String baseUrl;
            private String apiKey;
            private boolean enabled;

        }

        @Data
        public static class ModelDef {
            private String id;
            private String supplierId;
            private String modelName;
            private boolean stream;
            private boolean enabled;

        }
    }

    @Data
    public static class Tools {
        private List<String> enabled = new ArrayList<>();
        private long bashTimeoutSeconds;
    }

    @Data
    public static class Skills {
        private List<String> paths = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();
    }

    @Data
    public static class Permissions {
        private String mode = "ask";
        private List<String> deny = new ArrayList<>();
        private List<String> allow = new ArrayList<>();
    }

    @Data
    public static class Serve {
        private String authMode = "none";
        private int port = 8080;
    }

    @Data
    public static class Subagent {
        private int maxParallel = 2;
        private List<AgentDef> agents = new ArrayList<>();


        @Data
        public static class AgentDef {
            private String name;
            private String description;
            private List<String> tools = new ArrayList<>();
            private String workspaceMode;
        }
    }
}
