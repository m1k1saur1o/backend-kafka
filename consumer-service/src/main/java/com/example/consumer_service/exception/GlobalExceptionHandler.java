package com.example.consumer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.consumer_service.dto.ErrorResponse;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ListenerNoEncontradoException.class)
	public ResponseEntity<ErrorResponse> listenerNoEncontrado(ListenerNoEncontradoException ex) {

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> parametroInvalido(MethodArgumentTypeMismatchException ex) {

		return ResponseEntity.badRequest()
				.body(new ErrorResponse("invalid_request", "Parámetro inválido: " + ex.getName()));
	}

	@ExceptionHandler(RequestNotPermitted.class)
	public ResponseEntity<ErrorResponse> demasiadasSolicitudes(RequestNotPermitted ex) {

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("too_many_requests",
						"Se superó el límite de solicitudes. Intente nuevamente en unos segundos."));
	}
}
