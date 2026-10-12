package com.example.producer_service.service.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.producer_service.config.AuditoriaKafkaProperties;
import com.example.producer_service.config.KafkaAppProperties;
import com.example.producer_service.dto.BffCallEventDTO;
import com.example.producer_service.exception.CircuitoAbiertoException;
import com.example.producer_service.exception.PublicacionException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Publica eventos de auditoría de ms-bff en el tópico bff-call-events con la misma configuración
 * de productor que las notificaciones (acks=all, idempotencia, timeouts) y su propia instancia de
 * Resilience4j ("kafkaAuditoria"), para que un problema en un flujo no abra el circuito del otro.
 * La clave del mensaje es el eventId: los reintentos publican exactamente el mismo evento y el
 * consumidor puede descartar duplicados por eventId.
 */
@Component
public class KafkaAuditoriaPublisher {

	static final String INSTANCIA = "kafkaAuditoria";

	private static final Logger log = LoggerFactory.getLogger(KafkaAuditoriaPublisher.class);

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final AuditoriaKafkaProperties auditoria;
	private final KafkaAppProperties kafka;

	@SuppressWarnings("unchecked")
	public KafkaAuditoriaPublisher(KafkaTemplate<?, ?> kafkaTemplate, AuditoriaKafkaProperties auditoria,
			KafkaAppProperties kafka) {
		// Spring Boot crea un único KafkaTemplate (JsonSerializer) compartido por ambos flujos
		this.kafkaTemplate = (KafkaTemplate<String, Object>) kafkaTemplate;
		this.auditoria = auditoria;
		this.kafka = kafka;
	}

	@Retry(name = INSTANCIA)
	@CircuitBreaker(name = INSTANCIA, fallbackMethod = "circuitoAbierto")
	public RecordMetadata publicar(BffCallEventDTO evento) {

		try {
			return kafkaTemplate.send(auditoria.topic(), evento.eventId(), evento)
					.get(kafka.sendTimeout().toMillis(), TimeUnit.MILLISECONDS)
					.getRecordMetadata();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PublicacionException("Publicación interrumpida", e);
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			log.error("No se pudo publicar el evento de auditoría eventId={}: {}", evento.eventId(), e.toString());
			throw new PublicacionException("Kafka no confirmó la publicación del evento de auditoría.", e);
		}
	}

	@SuppressWarnings("unused")
	private RecordMetadata circuitoAbierto(BffCallEventDTO evento, CallNotPermittedException ex) {

		log.warn("Circuit breaker '{}' abierto: se rechaza el evento de auditoría eventId={} sin contactar a Kafka",
				INSTANCIA, evento.eventId());
		throw new CircuitoAbiertoException(
				"Kafka no está disponible temporalmente (circuit breaker abierto). Intente nuevamente en unos segundos.",
				ex);
	}
}
