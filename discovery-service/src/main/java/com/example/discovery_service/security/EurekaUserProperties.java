package com.example.discovery_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Credenciales HTTP Basic del registro Eureka. Los clientes las envían en la URL
 * (http://usuario:clave@discovery-service:8761/eureka/), por eso solo se admiten
 * caracteres que no requieren codificación en una URL.
 */
@Validated
@ConfigurationProperties(prefix = "app.eureka")
public record EurekaUserProperties(
		@NotBlank(message = "EUREKA_USERNAME es obligatorio")
		@Pattern(regexp = "[A-Za-z0-9._-]+", message = "EUREKA_USERNAME solo admite letras, números, '.', '_' y '-'") String username,
		@NotBlank(message = "EUREKA_PASSWORD es obligatorio")
		@Pattern(regexp = "[A-Za-z0-9._~-]{16,}", message = "EUREKA_PASSWORD debe tener al menos 16 caracteres [A-Za-z0-9._~-]") String password) {

	@Override
	public String toString() {
		return "EurekaUserProperties[username=" + username + ", password=****]";
	}
}
