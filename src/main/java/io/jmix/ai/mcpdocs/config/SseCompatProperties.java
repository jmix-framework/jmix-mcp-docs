package io.jmix.ai.mcpdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.server.sse-compat")
public class SseCompatProperties {

    private boolean enabled = false;
    private String sseEndpoint = "/sse";
    private String messageEndpoint = "/message";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public String getMessageEndpoint() {
        return messageEndpoint;
    }

    public void setMessageEndpoint(String messageEndpoint) {
        this.messageEndpoint = messageEndpoint;
    }
}
