package com.example.producer_service.exception;

/**
 * Kafka no confirmó la publicación del mensaje dentro del tiempo configurado.
 */
public class PublicacionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PublicacionException(String message, Throwable cause) {
		super(message, cause);
	}
}
