package com.example.auth_service.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cliente de servicio (máquina a máquina) habilitado para el grant client_credentials
 * en {@code POST /auth/token}. Se carga desde AUTH_CLIENT_ID, AUTH_CLIENT_SECRET y
 * AUTH_CLIENT_ROLES. Si AUTH_CLIENT_ID queda vacío el grant está deshabilitado.
 *
 * Es independiente del usuario de {@link AuthUserProperties}: un cliente no puede usar
 * {@code /auth/login} y el usuario no puede obtener tokens de servicio.
 * El secreto admite texto plano (se cifra con BCrypt al arrancar) o {@code {bcrypt}$2a$10$...}.
 */
@ConfigurationProperties(prefix = "app.auth.client")
public record ServiceClientProperties(String id, String secret, List<String> roles) {

	public ServiceClientProperties {
		id = id == null ? "" : id.trim();
		secret = secret == null ? "" : secret;
		roles = roles == null ? List.of()
				: roles.stream().map(String::trim).filter(r -> !r.isEmpty()).map(String::toUpperCase).toList();
	}

	public boolean habilitado() {
		return !id.isEmpty() && !secret.isEmpty();
	}

	@Override
	public String toString() {
		return "ServiceClientProperties[id=" + id + ", secret=****, roles=" + roles + "]";
	}
}
