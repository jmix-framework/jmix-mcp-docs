package io.jmix.ai.mcpdocs.docs;

import java.util.List;

public interface Chunker {

    record Chunk(String text, String anchor) {
    }

    List<Chunk> extract(String content, String docPath);
}
