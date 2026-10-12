package com.example.auth_service.service;

import org.springframework.security.core.Authentication;

public interface ClientCredentialsService {

	/**
	 * Valida client_id / client_secret del cliente de servicio.
	 *
	 * @throws com.example.auth_service.exception.ClienteInvalidoException si no coinciden o el grant está deshabilitado
	 */
	Authentication autenticarCliente(String clientId, String clientSecret);
}
