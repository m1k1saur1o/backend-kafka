package com.example.producer_service.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.producer_service.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mantiene el comportamiento estándar de RFC 6750 (cabecera WWW-Authenticate y códigos
 * 401/403) y añade un cuerpo JSON legible para clientes como Postman.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
	private final BearerTokenAccessDeniedHandler deniedHandler = new BearerTokenAccessDeniedHandler();
	private final ObjectMapper objectMapper;

	public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		entryPoint.commence(request, response, authException);

		String detalle = request.getHeader("Authorization") == null
				? "Falta el token. Envíe Authorization: Bearer <JWT>"
				: "Token inválido, expirado o no emitido por el servidor de autenticación";
		write(response, HttpStatus.UNAUTHORIZED, new ErrorResponse("invalid_token", detalle));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		deniedHandler.handle(request, response, accessDeniedException);
		write(response, HttpStatus.FORBIDDEN,
				new ErrorResponse("insufficient_scope", "El token no tiene los roles necesarios"));
	}

	private void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
