package com.example.auth_service.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Usuario habilitado para autenticarse. Se carga desde variables de entorno
 * (AUTH_USERNAME, AUTH_PASSWORD, AUTH_ROLES); la aplicación no arranca si faltan.
 *
 * La contraseña puede entregarse en texto plano (se cifra con BCrypt al arrancar)
 * o ya codificada con prefijo de algoritmo, por ejemplo {@code {bcrypt}$2a$10$...}.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthUserProperties(
		@NotBlank(message = "AUTH_USERNAME es obligatorio") String username,
		@NotBlank(message = "AUTH_PASSWORD es obligatorio") String password,
		@NotEmpty(message = "AUTH_ROLES debe contener al menos un rol") List<String> roles) {

	@Override
	public String toString() {
		return "AuthUserProperties[username=" + username + ", password=****, roles=" + roles + "]";
	}
}
