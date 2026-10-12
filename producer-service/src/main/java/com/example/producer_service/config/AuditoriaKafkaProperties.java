package com.example.producer_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Tópico de auditoría de llamadas a ms-bff (separado del tópico de notificaciones).
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka.auditoria")
public record AuditoriaKafkaProperties(
		@NotBlank String topic,
		@Min(1) int partitions,
		@Min(1) short replicas,
		@NotNull Duration retention) {
}
