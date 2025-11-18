package io.jmix.ai.mcpdocs.entity;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Map;
import java.util.UUID;

@Table(name = "MCP_VECTOR_STORE")
@Entity
public class VectorStoreEntity {
    @GeneratedValue
    @Id
    private UUID id;

    private String content;

    private String metadata;

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadataMap() {
        try {
            return new ObjectMapper().readValue(metadata, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
