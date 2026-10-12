package com.example.producer_service.service.impl;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.producer_service.dto.AuditoriaPublicacionResponse;
import com.example.producer_service.dto.BffCallEventDTO;
import com.example.producer_service.service.AuditoriaProducerService;

@Service
public class AuditoriaProducerServiceImpl implements AuditoriaProducerService {

	private static final Logger log = LoggerFactory.getLogger(AuditoriaProducerServiceImpl.class);

	private final KafkaAuditoriaPublisher publisher;

	public AuditoriaProducerServiceImpl(KafkaAuditoriaPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public AuditoriaPublicacionResponse publicarEvento(BffCallEventDTO evento) {

		// El evento llega completo desde ms-bff (eventId incluido) y se publica sin modificar
		RecordMetadata metadata = publisher.publicar(evento);
		log.info("Evento de auditoría publicado eventId={} requestId={} {} {} -> {} topic={} partition={} offset={}",
				evento.eventId(), evento.requestId(), evento.method(), evento.path(), evento.status(),
				metadata.topic(), metadata.partition(), metadata.offset());

		return new AuditoriaPublicacionResponse("PUBLICADO", evento.eventId(), metadata.topic(), metadata.partition(),
				metadata.offset());
	}
}
