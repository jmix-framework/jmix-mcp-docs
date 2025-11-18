package io.jmix.ai.mcpdocs.docs;


import io.jmix.ai.mcpdocs.entity.VectorStoreEntity;

public interface Ingester {

    String getType();

    String updateAll();

    String update(VectorStoreEntity entity);
}
