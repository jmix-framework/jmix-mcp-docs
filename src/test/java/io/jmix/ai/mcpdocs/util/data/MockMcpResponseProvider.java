package io.jmix.ai.mcpdocs.util.data;

import io.jmix.ai.mcpdocs.util.mcp.MockSearchResult;

/**
 * Provides mock responses for testing using Jackson DTOs.
 */
public class MockMcpResponseProvider {

    public static final String JMIX_DOCS_SEARCH_RESPONSE = MockSearchResult.create()
            .addItem(
                    "DataGrid Component",
                    "DataGrid is a powerful component for displaying tabular data...",
                    "https://docs.jmix.io/jmix/flow-ui/vc/components/dataGrid.html",
                    0.95
            )
            .addItem(
                    "Grid Columns",
                    "Configure columns in DataGrid using column definitions...",
                    "https://docs.jmix.io/jmix/flow-ui/vc/components/dataGrid.html#columns",
                    0.87
            )
            .asJson();

    public static final String ENTITY_LISTENERS_RESPONSE = MockSearchResult.create()
            .addItem(
                    "Entity Listeners",
                    "Entity listeners are used to handle entity lifecycle events...",
                    "https://docs.jmix.io/jmix/data-model/entity-listeners.html",
                    0.92
            )
            .asJson();

    public static final String EMPTY_RESPONSE = MockSearchResult.create().asJson();

    private MockMcpResponseProvider() {
        // Utility class
    }
}
