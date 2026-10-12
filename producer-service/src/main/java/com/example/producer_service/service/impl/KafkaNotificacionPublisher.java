package com.example.producer_service.service.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.producer_service.config.KafkaAppProperties;
import com.example.producer_service.dto.NotificacionDTO;
import com.example.producer_service.exception.CircuitoAbiertoException;
import com.example.producer_service.exception.PublicacionException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Envía la notificación a Kafka protegida con Resilience4j (instancia "kafkaProducer"):
 * <ul>
 * <li>{@code @Retry}: reintenta la publicación del mismo mensaje (mismo id) si Kafka no la confirma.</li>
 * <li>{@code @CircuitBreaker}: si la tasa de fallos supera el umbral, el circuito se abre y las
 * solicitudes siguientes fallan de inmediato (503) sin esperar el timeout de Kafka.</li>
 * </ul>
 * Orden de ejecución: Retry( CircuitBreaker( envío ) ). Con el circuito abierto no se reintenta.
 */
@Component
public class KafkaNotificacionPublisher {

	static final String INSTANCIA = "kafkaProducer";

	private static final Logger log = LoggerFactory.getLogger(KafkaNotificacionPublisher.class);

	private final KafkaTemplate<String, NotificacionDTO> kafkaTemplate;
	private final KafkaAppProperties props;

	public KafkaNotificacionPublisher(KafkaTemplate<String, NotificacionDTO> kafkaTemplate, KafkaAppProperties props) {
		this.kafkaTemplate = kafkaTemplate;
		this.props = props;
	}

	@Retry(name = INSTANCIA)
	@CircuitBreaker(name = INSTANCIA, fallbackMethod = "circuitoAbierto")
	public RecordMetadata publicar(NotificacionDTO notificacion) {

		try {
			// Se espera la confirmación del broker (acks=all) para responder con datos reales
			return kafkaTemplate.send(props.topic(), notificacion.id(), notificacion)
					.get(props.sendTimeout().toMillis(), TimeUnit.MILLISECONDS)
					.getRecordMetadata();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PublicacionException("Publicación interrumpida", e);
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			log.error("No se pudo publicar la notificación id={}: {}", notificacion.id(), e.toString());
			throw new PublicacionException("Kafka no confirmó la publicación del mensaje. Intente nuevamente.", e);
		}
	}

	/**
	 * Fallback del Circuit Breaker: solo se aplica cuando el circuito está abierto. Los demás
	 * errores (PublicacionException) se propagan para que @Retry los reintente.
	 */
	@SuppressWarnings("unused")
	private RecordMetadata circuitoAbierto(NotificacionDTO notificacion, CallNotPermittedException ex) {

		log.warn("Circuit breaker '{}' abierto: se rechaza la notificación id={} sin contactar a Kafka",
				INSTANCIA, notificacion.id());
		throw new CircuitoAbiertoException(
				"Kafka no está disponible temporalmente (circuit breaker abierto). Intente nuevamente en unos segundos.",
				ex);
	}
}
