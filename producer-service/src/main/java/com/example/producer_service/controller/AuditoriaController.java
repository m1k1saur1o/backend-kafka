package com.example.producer_service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.producer_service.dto.AuditoriaPublicacionResponse;
import com.example.producer_service.dto.BffCallEventDTO;
import com.example.producer_service.service.AuditoriaProducerService;

import jakarta.validation.Valid;

/**
 * Recibe los eventos de auditoría que genera ms-bff por cada llamada HTTP y los publica en el
 * tópico bff-call-events. Requiere un token de servicio con rol AUDIT_PUBLISHER (grant
 * client_credentials de auth-service); un token de usuario recibe 403.
 */
@RestController
public class AuditoriaController {

	private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

	private final AuditoriaProducerService auditoriaService;

	public AuditoriaController(AuditoriaProducerService auditoriaService) {
		this.auditoriaService = auditoriaService;
	}

	@PostMapping(path = "/auditoria/eventos", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AuditoriaPublicacionResponse> publicarEvento(@Valid @RequestBody BffCallEventDTO evento,
			@AuthenticationPrincipal Jwt jwt) {

		log.debug("Evento de auditoría recibido de '{}' eventId={}", jwt.getSubject(), evento.eventId());

		return ResponseEntity.status(HttpStatus.CREATED).body(auditoriaService.publicarEvento(evento));
	}
}
