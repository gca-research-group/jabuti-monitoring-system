package br.edu.unijui.gca.api.identity.adapters.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The raw key is available only in the creation response. */
public record CreateApiKeyResponse(Long userId, @JsonProperty("keyHash") String apiKey) { }
