package com.example.producer_service.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.producer_service.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
				.body(new ErrorResponse("invalid_request", "El cuerpo debe ser un JSON válido, por ejemplo {\"usuario\":\"ana\"}"));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException ex) {

		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(new ErrorResponse("invalid_request", "Content-Type debe ser application/json"));
	}

	@ExceptionHandler(PublicacionException.class)
	public ResponseEntity<ErrorResponse> kafkaNoDisponible(PublicacionException ex) {

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("kafka_unavailable", ex.getMessage()));
	}
}
