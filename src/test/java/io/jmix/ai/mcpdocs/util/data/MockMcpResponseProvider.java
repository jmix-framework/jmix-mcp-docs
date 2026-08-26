package io.jmix.ai.mcpdocs.util.data;

import io.jmix.ai.mcpdocs.util.mcp.MockSearchResult;

/**
 * Provides mock responses for testing using Jackson DTOs.
 */
public class MockMcpResponseProvider {

    public static final String JMIX_DOCS_SEARCH_RESPONSE = MockSearchResult.create()
            .addItem(
                    "DataGrid Component",
                    "https://docs.jmix.io/jmix/flow-ui/vc/components/dataGrid.html",
                    "DataGrid is a powerful component for displaying tabular data..."
            )
            .addItem(
                    "Grid Columns",
                    "https://docs.jmix.io/jmix/flow-ui/vc/components/dataGrid.html#columns",
                    "Configure columns in DataGrid using column definitions..."
            )
            .asJson();

    public static final String ENTITY_LISTENERS_RESPONSE = MockSearchResult.create()
            .addItem(
                    "Entity Listeners",
                    "https://docs.jmix.io/jmix/data-model/entity-listeners.html",
                    "Entity listeners are used to handle entity lifecycle events..."
            )
            .asJson();

    public static final String EMPTY_RESPONSE = MockSearchResult.create().asJson();

    private MockMcpResponseProvider() {
        // Utility class
    }
}
