package com.example.consumer_service.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.consumer_service.service.HistorialMensajesService;

/**
 * Consulta de los últimos mensajes procesados por esta instancia (historial en memoria).
 */
@RestController
public class MensajesController {

	private final HistorialMensajesService historial;

	public MensajesController(HistorialMensajesService historial) {
		this.historial = historial;
	}

	@GetMapping("/notificaciones/consumidas")
	public Map<String, Object> ultimasConsumidas(@RequestParam(defaultValue = "20") int limite) {

		int acotado = Math.max(1, Math.min(limite, 1000));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("totalProcesados", historial.totalProcesados());
		body.put("mensajes", historial.ultimos(acotado));
		return body;
	}
}
