package com.example.producer_service.service.impl;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.example.producer_service.config.KafkaAppProperties;
import com.example.producer_service.dto.NotificacionDTO;
import com.example.producer_service.dto.NotificacionRequest;
import com.example.producer_service.dto.PublicacionResponse;
import com.example.producer_service.exception.PublicacionException;
import com.example.producer_service.service.KafkaProducerService;

@Service
public class KafkaProducerServiceImpl implements KafkaProducerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaProducerServiceImpl.class);

	private final KafkaTemplate<String, NotificacionDTO> kafkaTemplate;
	private final KafkaAppProperties props;

	public KafkaProducerServiceImpl(KafkaTemplate<String, NotificacionDTO> kafkaTemplate, KafkaAppProperties props) {
		this.kafkaTemplate = kafkaTemplate;
		this.props = props;
	}

	@Override
	public PublicacionResponse enviarNotificacion(NotificacionRequest request) {

		NotificacionDTO notificacion = new NotificacionDTO(
				UUID.randomUUID().toString(), Instant.now().toString(), request.usuario().trim());

		try {
			// Se espera la confirmación del broker (acks=all) para responder con datos reales
			SendResult<String, NotificacionDTO> result = kafkaTemplate
					.send(props.topic(), notificacion.id(), notificacion)
					.get(props.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);

			RecordMetadata metadata = result.getRecordMetadata();
			log.info("Notificación publicada id={} topic={} partition={} offset={}",
					notificacion.id(), metadata.topic(), metadata.partition(), metadata.offset());

			return new PublicacionResponse("PUBLICADA", notificacion, metadata.topic(), metadata.partition(),
					metadata.offset());

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PublicacionException("Publicación interrumpida", e);
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			log.error("No se pudo publicar la notificación id={}: {}", notificacion.id(), e.toString());
			throw new PublicacionException("Kafka no confirmó la publicación del mensaje. Intente nuevamente.", e);
		}
	}
}
