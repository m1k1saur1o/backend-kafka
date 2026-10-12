package com.example.auth_service.config;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parámetros de emisión del JWT. El secreto HS256 debe tener al menos 256 bits
 * (32 bytes) y debe ser el mismo que usan producer-service, consumer-service y ms-bff.
 *
 * {@code audience} es la audiencia principal (JWT_AUDIENCE, validada por producer y consumer);
 * {@code extraAudiences} agrega otras APIs que aceptan el mismo token (JWT_EXTRA_AUDIENCES,
 * por ejemplo la audiencia propia de ms-bff).
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank(message = "JWT_SECRET es obligatorio")
		@Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres (256 bits)") String secret,
		@NotBlank String issuer,
		@NotBlank String audience,
		@NotNull Duration expiration,
		List<String> extraAudiences) {

	@ConstructorBinding
	public JwtProperties {
		extraAudiences = extraAudiences == null ? List.of()
				: extraAudiences.stream().map(String::trim).filter(a -> !a.isEmpty()).toList();
	}

	public JwtProperties(String secret, String issuer, String audience, Duration expiration) {
		this(secret, issuer, audience, expiration, List.of());
	}

	/** Audiencia principal seguida de las adicionales, sin duplicados. */
	public List<String> audiences() {
		return Stream.concat(Stream.of(audience), extraAudiences.stream()).distinct().toList();
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=****, issuer=" + issuer + ", audiences=" + audiences() + ", expiration="
				+ expiration + "]";
	}
}
