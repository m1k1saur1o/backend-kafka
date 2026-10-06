package com.example.auth_service.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.auth_service.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> credencialesInvalidas(AuthenticationException ex) {

		log.warn("Intento de autenticación fallido: {}", ex.getClass().getSimpleName());

		// Mensaje genérico: no se revela si falló el usuario o la contraseña
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header("WWW-Authenticate", "Bearer")
				.body(new ErrorResponse("invalid_grant", "Usuario o contraseña incorrectos"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {

		Map<String, String> details = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(e -> details.putIfAbsent(e.getField(), e.getDefaultMessage()));

		return ResponseEntity.badRequest()
				.body(new ErrorResponse("invalid_request", "El cuerpo de la solicitud no es válido", details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> jsonInvalido(HttpMessageNotReadableException ex) {

		return ResponseEntity.badRequest()
				.body(new ErrorResponse("invalid_request", "El cuerpo debe ser un JSON válido con username y password"));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException ex) {

		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(new ErrorResponse("invalid_request", "Content-Type debe ser application/json"));
	}
}
