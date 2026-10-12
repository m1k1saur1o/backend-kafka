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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.auth_service.dto.ErrorResponse;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

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
				.body(new ErrorResponse("invalid_request",
						"Content-Type no soportado: /auth/login usa application/json y /auth/token application/x-www-form-urlencoded"));
	}

	@ExceptionHandler(ClienteInvalidoException.class)
	public ResponseEntity<ErrorResponse> clienteInvalido(ClienteInvalidoException ex) {

		log.warn("Autenticación de cliente de servicio fallida");

		// RFC 6749 §5.2: invalid_client con 401
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header("WWW-Authenticate", "Bearer")
				.body(new ErrorResponse("invalid_client", "Cliente no autorizado"));
	}

	@ExceptionHandler(GrantNoSoportadoException.class)
	public ResponseEntity<ErrorResponse> grantNoSoportado(GrantNoSoportadoException ex) {

		return ResponseEntity.badRequest().body(new ErrorResponse("unsupported_grant_type", ex.getMessage()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException ex) {

		return ResponseEntity.badRequest()
				.body(new ErrorResponse("invalid_request", "Falta el parámetro " + ex.getParameterName()));
	}

	@ExceptionHandler(RequestNotPermitted.class)
	public ResponseEntity<ErrorResponse> demasiadosIntentos(RequestNotPermitted ex) {

		log.warn("Rate limiter de login activado: {}", ex.getMessage());

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("too_many_requests",
						"Demasiados intentos de inicio de sesión. Intente nuevamente en unos segundos."));
	}
}
