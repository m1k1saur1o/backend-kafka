package com.example.consumer_service.dto;

/**
 * Mensaje leído desde Kafka (JSON): id, fecha (ISO-8601 UTC) y usuario.
 */
public record NotificacionDTO(String id, String fecha, String usuario) {
}
