package com.example.consumer_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.consumer_service.dto.ListenerEstadoResponse;
import com.example.consumer_service.service.KafkaAdminListenerService;

/**
 * Administración del listener Kafka. Requiere JWT: GET con rol USER/ADMIN, POST con rol ADMIN.
 */
@RestController
@RequestMapping("/kafka-listener")
public class KafkaAdminListenerController {

	private final KafkaAdminListenerService service;

	public KafkaAdminListenerController(KafkaAdminListenerService service) {
		this.service = service;
	}

	@GetMapping
	public List<ListenerEstadoResponse> listarListeners() {

		return service.listarListeners();
	}

	@PostMapping("/{id}/pausar")
	public ListenerEstadoResponse pausarListener(@PathVariable String id) {

		return service.pausarListener(id);
	}

	@PostMapping("/{id}/reanudar")
	public ListenerEstadoResponse reanudarListener(@PathVariable String id) {

		return service.reanudarListener(id);
	}

	@GetMapping("/{id}/estado")
	public ListenerEstadoResponse obtenerEstadoListener(@PathVariable String id) {

		return service.obtenerEstadoListener(id);
	}
}
