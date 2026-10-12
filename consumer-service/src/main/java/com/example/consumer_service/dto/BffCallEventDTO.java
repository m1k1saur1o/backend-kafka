package com.example.consumer_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Evento de auditoría de una llamada HTTP a ms-bff leído desde el tópico bff-call-events
 * (esquema v1, ver docs/EVENTO_AUDITORIA_BFF.md). Se ignoran campos desconocidos para aceptar
 * versiones menores posteriores (1.1, 1.2...) que solo agreguen campos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BffCallEventDTO(
		String schemaVersion,
		String eventType,
		String eventId,
		String timestamp,
		String requestId,
		String sourceService,
		String sourceInstance,
		String method,
		String path,
		String routePattern,
		String endpoint,
		String channel,
		String user,
		boolean authenticated,
		int status,
		String outcome,
		long durationMs,
		String errorCode,
		String errorMessage) {
}
