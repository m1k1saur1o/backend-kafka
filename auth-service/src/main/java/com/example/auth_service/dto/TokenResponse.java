package com.example.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta con el formato de un token response OAuth2 (RFC 6749, sección 5.1).
 */
public record TokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_in") long expiresIn) {
}
