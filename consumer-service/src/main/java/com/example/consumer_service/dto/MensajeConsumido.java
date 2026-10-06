package com.example.consumer_service.dto;

public record MensajeConsumido(
		NotificacionDTO notificacion,
		String topic,
		int particion,
		long offset,
		String consumidoEn) {
}
