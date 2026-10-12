package com.example.consumer_service.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.consumer_service.service.HistorialAuditoriaService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * Consulta de los últimos eventos de auditoría de ms-bff procesados por esta instancia.
 * Requiere JWT con rol USER o ADMIN. Filtros opcionales: requestId, canal y status.
 */
@RestController
@RateLimiter(name = "consumerApi")
public class AuditoriaController {

	private final HistorialAuditoriaService historial;

	public AuditoriaController(HistorialAuditoriaService historial) {
		this.historial = historial;
	}

	@GetMapping("/auditoria/eventos")
	public Map<String, Object> ultimosEventos(@RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String requestId, @RequestParam(required = false) String canal,
			@RequestParam(required = false) Integer status) {

		int acotado = Math.max(1, Math.min(limite, 1000));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("totalProcesados", historial.totalProcesados());
		body.put("duplicadosDescartados", historial.duplicadosDescartados());
		body.put("porCanal", historial.totalesPorCanal());
		body.put("porResultado", historial.totalesPorResultado());
		body.put("eventos", historial.ultimos(acotado, requestId, canal, status));
		return body;
	}
}
