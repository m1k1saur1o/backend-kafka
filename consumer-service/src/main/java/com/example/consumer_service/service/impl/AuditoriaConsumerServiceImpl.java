package com.example.consumer_service.service.impl;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.example.consumer_service.config.AuditoriaKafkaConsumerConfig;
import com.example.consumer_service.dto.BffCallEventDTO;
import com.example.consumer_service.dto.EventoAuditoriaConsumido;
import com.example.consumer_service.exception.EventoInvalidoException;
import com.example.consumer_service.service.AuditoriaConsumerService;
import com.example.consumer_service.service.HistorialAuditoriaService;

/**
 * Procesa en tiempo real los eventos de auditoría de ms-bff (tópico bff-call-events).
 * El offset se confirma solo después de registrar el evento (AckMode MANUAL_IMMEDIATE). Los
 * duplicados (mismo eventId) se confirman sin volver a registrarse: el procesamiento es idempotente.
 */
@Service
public class AuditoriaConsumerServiceImpl implements AuditoriaConsumerService {

	private static final Logger log = LoggerFactory.getLogger(AuditoriaConsumerServiceImpl.class);

	private final HistorialAuditoriaService historial;

	public AuditoriaConsumerServiceImpl(HistorialAuditoriaService historial) {
		this.historial = historial;
	}

	@Override
	@KafkaListener(id = "${app.kafka.auditoria.listener-id}", topics = "${app.kafka.auditoria.topic}",
			groupId = "${app.kafka.auditoria.group-id}", containerFactory = AuditoriaKafkaConsumerConfig.CONTAINER_FACTORY,
			autoStartup = "${app.kafka.auto-startup:true}")
	public void consumirEvento(ConsumerRecord<String, BffCallEventDTO> record, Acknowledgment ack) {

		BffCallEventDTO evento = validar(record.value());

		boolean nuevo = historial.registrarSiEsNuevo(new EventoAuditoriaConsumido(evento, record.topic(),
				record.partition(), record.offset(), Instant.now().toString()));

		if (nuevo) {
			log.info("Evento de auditoría consumido partition={} offset={} eventId={} requestId={} user={} {} {} -> {} ({} ms)",
					record.partition(), record.offset(), evento.eventId(), evento.requestId(), evento.user(),
					evento.method(), evento.path(), evento.status(), evento.durationMs());
		} else {
			log.warn("Evento de auditoría duplicado eventId={} partition={} offset={}: se confirma sin reprocesar",
					evento.eventId(), record.partition(), record.offset());
		}

		ack.acknowledge();
	}

	private static BffCallEventDTO validar(BffCallEventDTO evento) {

		if (evento == null || evento.eventId() == null || evento.eventId().isBlank()) {
			throw new EventoInvalidoException("Evento de auditoría vacío o sin eventId");
		}
		if (evento.schemaVersion() == null || !evento.schemaVersion().startsWith("1.")) {
			throw new EventoInvalidoException("schemaVersion no soportada: " + evento.schemaVersion());
		}
		return evento;
	}
}
