package com.example.producer_service.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.producer_service.dto.NotificacionDTO;
import com.example.producer_service.dto.NotificacionRequest;
import com.example.producer_service.dto.PublicacionResponse;
import com.example.producer_service.service.KafkaProducerService;

@Service
public class KafkaProducerServiceImpl implements KafkaProducerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaProducerServiceImpl.class);

	private final KafkaNotificacionPublisher publisher;

	public KafkaProducerServiceImpl(KafkaNotificacionPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public PublicacionResponse enviarNotificacion(NotificacionRequest request) {

		// El id y la fecha se generan una sola vez: los reintentos publican exactamente el mismo mensaje
		NotificacionDTO notificacion = new NotificacionDTO(
				UUID.randomUUID().toString(), Instant.now().toString(), request.usuario().trim());

		RecordMetadata metadata = publisher.publicar(notificacion);
		log.info("Notificación publicada id={} topic={} partition={} offset={}",
				notificacion.id(), metadata.topic(), metadata.partition(), metadata.offset());

		return new PublicacionResponse("PUBLICADA", notificacion, metadata.topic(), metadata.partition(),
				metadata.offset());
	}
}
