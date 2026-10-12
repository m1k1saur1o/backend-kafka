package com.example.consumer_service.exception;

/**
 * El mensaje se deserializó pero no cumple el contrato (campos obligatorios o versión).
 * No se reintenta: el error handler lo registra y confirma su offset.
 */
public class EventoInvalidoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EventoInvalidoException(String message) {
		super(message);
	}
}
