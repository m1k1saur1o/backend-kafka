package com.example.producer_service.exception;

/**
 * El Circuit Breaker de Kafka está abierto: la solicitud se rechaza sin intentar publicar.
 * No extiende PublicacionException para que @Retry no la reintente.
 */
public class CircuitoAbiertoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CircuitoAbiertoException(String message, Throwable cause) {
		super(message, cause);
	}
}
