package com.example.consumer_service.exception;

public class ListenerNoEncontradoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ListenerNoEncontradoException(String id) {
		super("No existe un listener con id '" + id + "'");
	}
}
