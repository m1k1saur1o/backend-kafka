package com.example.consumer_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de Kafka propia del servicio (el resto usa spring.kafka.*).
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaAppProperties(
		@NotBlank(message = "KAFKA_BOOTSTRAP_SERVERS es obligatorio") String bootstrapServers,
		@NotBlank String topic,
		@NotBlank String groupId,
		@NotBlank String listenerId,
		@Min(0) @Max(10) int retryAttempts,
		@NotNull Duration retryBackoff,
		@Min(1) @Max(1000) int historySize) {
}
