package com.example.consumer_service.dto;

public record EventoAuditoriaConsumido(
		BffCallEventDTO evento,
		String topic,
		int particion,
		long offset,
		String consumidoEn) {
}
