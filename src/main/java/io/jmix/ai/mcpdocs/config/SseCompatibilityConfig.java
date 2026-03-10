package io.jmix.ai.mcpdocs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * Registers an additional SSE transport alongside the primary Streamable HTTP transport.
 * This provides backward compatibility for clients that only support the deprecated SSE protocol.
 * <p>
 * Enabled via {@code mcp.server.sse-compat.enabled=true}.
 * <p>
 * Creates a separate MCP server instance for SSE transport with the same tool registrations
 * as the primary Streamable HTTP server.
 */
@Configuration
@ConditionalOnProperty(name = "mcp.server.sse-compat.enabled", havingValue = "true")
@EnableConfigurationProperties(SseCompatProperties.class)
public class SseCompatibilityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SseCompatibilityConfig.class);

    /**
     * Holds both the SSE transport provider and its MCP server.
     * Wrapping them together avoids exposing {@link WebMvcSseServerTransportProvider} as
     * a standalone bean, which would conflict with the auto-configured Streamable HTTP
     * transport (Spring expects a single {@code McpServerTransportProviderBase} bean).
     */
    record SseServerHolder(WebMvcSseServerTransportProvider transport, McpSyncServer server) {
    }

    @Bean
    SseServerHolder sseServerHolder(
            ObjectProvider<ObjectMapper> objectMapper,
            SseCompatProperties sseProps,
            McpServerProperties serverProperties,
            ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> toolSpecs
    ) {
        logger.info("Registering SSE compatibility transport: sseEndpoint={}, messageEndpoint={}",
                sseProps.getSseEndpoint(), sseProps.getMessageEndpoint());

        WebMvcSseServerTransportProvider sseTransport = WebMvcSseServerTransportProvider.builder()
                .objectMapper(objectMapper.getIfAvailable(ObjectMapper::new))
                .sseEndpoint(sseProps.getSseEndpoint())
                .messageEndpoint(sseProps.getMessageEndpoint())
                .build();

        var spec = McpServer.sync(sseTransport)
                .serverInfo(serverProperties.getName(), serverProperties.getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build());

        toolSpecs.ifAvailable(spec::tools);

        McpSyncServer server = spec.build();
        logger.info("SSE compatibility MCP server started");

        return new SseServerHolder(sseTransport, server);
    }

    @Bean
    RouterFunction<ServerResponse> sseMcpRouterFunction(SseServerHolder holder) {
        return holder.transport().getRouterFunction();
    }
}
