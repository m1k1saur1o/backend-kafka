package com.example.producer_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Evento de auditoría de una llamada HTTP recibida por ms-bff (esquema v1, tópico bff-call-events).
 * Es un contrato distinto de {@link NotificacionDTO}: nunca se publica en el tópico Notificaciones.
 *
 * Versionado: {@code schemaVersion} "1.x". Un cambio compatible (agregar campos opcionales) sube
 * la versión menor; un cambio incompatible exige "2.0" y un tópico o eventType nuevo.
 * El evento no incluye tokens, contraseñas, cabeceras ni cuerpos de la solicitud.
 * Esquema completo: docs/EVENTO_AUDITORIA_BFF.md.
 */
public record BffCallEventDTO(
		@NotBlank @Pattern(regexp = "1\\.\\d+", message = "schemaVersion soportada: 1.x") String schemaVersion,
		@NotBlank @Pattern(regexp = "BFF_HTTP_CALL", message = "eventType debe ser BFF_HTTP_CALL") String eventType,
		@NotBlank @Size(max = 64) String eventId,
		@NotBlank @Size(max = 40) String timestamp,
		@NotBlank @Size(max = 64) String requestId,
		@NotBlank @Size(max = 64) String sourceService,
		@Size(max = 128) String sourceInstance,
		@NotBlank @Size(max = 10) String method,
		@NotBlank @Size(max = 2048) String path,
		@Size(max = 256) String routePattern,
		@NotBlank @Size(max = 64) String endpoint,
		@NotBlank @Size(max = 32) String channel,
		@Size(max = 128) String user,
		boolean authenticated,
		@Min(100) @Max(599) int status,
		@NotBlank @Pattern(regexp = "SUCCESS|CLIENT_ERROR|SERVER_ERROR") String outcome,
		@PositiveOrZero long durationMs,
		@Size(max = 64) String errorCode,
		@Size(max = 500) String errorMessage) {
}
