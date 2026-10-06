package com.example.producer_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de Kafka propia del servicio (el resto usa spring.kafka.*).
 * bootstrapServers se valida aquí para fallar al arrancar si KAFKA_BOOTSTRAP_SERVERS no está definido.
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaAppProperties(
		@NotBlank(message = "KAFKA_BOOTSTRAP_SERVERS es obligatorio") String bootstrapServers,
		@NotBlank String topic,
		@Min(1) int partitions,
		@Min(1) short replicas,
		@NotNull Duration sendTimeout) {
}
