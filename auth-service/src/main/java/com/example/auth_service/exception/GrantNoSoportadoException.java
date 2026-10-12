package com.example.auth_service.exception;

/**
 * grant_type distinto de client_credentials en POST /auth/token (400 unsupported_grant_type).
 */
public class GrantNoSoportadoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GrantNoSoportadoException() {
		super("Solo se admite grant_type=client_credentials");
	}
}
