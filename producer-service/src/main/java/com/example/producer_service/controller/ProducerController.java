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

import com.example.producer_service.dto.NotificacionRequest;
import com.example.producer_service.dto.PublicacionResponse;
import com.example.producer_service.service.KafkaProducerService;

import jakarta.validation.Valid;

@RestController
public class ProducerController {

	private static final Logger log = LoggerFactory.getLogger(ProducerController.class);

	private final KafkaProducerService producerService;

	public ProducerController(KafkaProducerService producerService) {
		this.producerService = producerService;
	}

	@PostMapping(path = "/notificaciones", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PublicacionResponse> crearNotificacion(@Valid @RequestBody NotificacionRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		log.debug("Solicitud de publicación realizada por '{}'", jwt.getSubject());

		return ResponseEntity.status(HttpStatus.CREATED).body(producerService.enviarNotificacion(request));
	}
}
