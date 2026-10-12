package com.example.consumer_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Consumo del tópico de auditoría de ms-bff. Usa su propio group.id, de modo que su avance de
 * offsets es independiente del consumidor de Notificaciones.
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka.auditoria")
public record AuditoriaKafkaProperties(
		@NotBlank String topic,
		@NotBlank String groupId,
		@NotBlank String listenerId,
		@Min(1) @Max(5000) int historySize) {
}
