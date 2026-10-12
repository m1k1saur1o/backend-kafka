package com.example.auth_service.exception;

/**
 * client_id / client_secret inválidos o grant client_credentials deshabilitado (401 invalid_client).
 */
public class ClienteInvalidoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ClienteInvalidoException() {
		super("Cliente no autorizado");
	}
}
