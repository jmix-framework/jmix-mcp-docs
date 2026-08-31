package io.jmix.ai.mcpdocs.service;

/**
 * Optional search parameters of the backend /api/v2/search contract.
 * Every field is nullable; a null (or blank version) field is omitted from
 * the request so the backend applies its own default. Values are not
 * validated here — the backend owns the contract and rejects invalid ones
 * with an error that {@link JmixContentSearchService#search} converts into
 * a readable message.
 */
public record SearchOptions(String jmixVersion, Integer maxResults, Integer tokens) {

    public static final SearchOptions NONE = new SearchOptions(null, null, null);
}
