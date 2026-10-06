package com.example.producer_service.dto;

/**
 * Mensaje publicado en Kafka (JSON). Equivale al DTO del productor de referencia:
 * id, fecha (ISO-8601 UTC) y usuario.
 */
public record NotificacionDTO(String id, String fecha, String usuario) {
}
