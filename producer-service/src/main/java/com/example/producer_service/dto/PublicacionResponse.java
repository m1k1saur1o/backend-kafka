package com.example.producer_service.dto;

public record PublicacionResponse(
		String estado,
		NotificacionDTO notificacion,
		String topic,
		int particion,
		long offset) {
}
