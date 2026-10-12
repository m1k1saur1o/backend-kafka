package com.example.producer_service.dto;

public record AuditoriaPublicacionResponse(
		String estado,
		String eventId,
		String topic,
		int particion,
		long offset) {
}
