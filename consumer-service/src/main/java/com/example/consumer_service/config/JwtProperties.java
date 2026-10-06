package com.example.consumer_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Parámetros para validar los JWT emitidos por auth-service (mismo secreto, issuer y audience).
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank(message = "JWT_SECRET es obligatorio")
		@Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres (256 bits)") String secret,
		@NotBlank String issuer,
		@NotBlank String audience) {

	@Override
	public String toString() {
		return "JwtProperties[secret=****, issuer=" + issuer + ", audience=" + audience + "]";
	}
}
