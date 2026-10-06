package com.example.auth_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parámetros de emisión del JWT. El secreto HS256 debe tener al menos 256 bits
 * (32 bytes) y debe ser el mismo que usan producer-service y consumer-service.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank(message = "JWT_SECRET es obligatorio")
		@Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres (256 bits)") String secret,
		@NotBlank String issuer,
		@NotBlank String audience,
		@NotNull Duration expiration) {

	@Override
	public String toString() {
		return "JwtProperties[secret=****, issuer=" + issuer + ", audience=" + audience + ", expiration="
				+ expiration + "]";
	}
}
