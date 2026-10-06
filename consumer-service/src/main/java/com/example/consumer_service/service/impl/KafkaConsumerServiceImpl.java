package com.example.consumer_service.service.impl;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.example.consumer_service.dto.MensajeConsumido;
import com.example.consumer_service.dto.NotificacionDTO;
import com.example.consumer_service.service.HistorialMensajesService;
import com.example.consumer_service.service.KafkaConsumerService;

@Service
public class KafkaConsumerServiceImpl implements KafkaConsumerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerServiceImpl.class);

	private final HistorialMensajesService historial;

	public KafkaConsumerServiceImpl(HistorialMensajesService historial) {
		this.historial = historial;
	}

	@Override
	@KafkaListener(id = "${app.kafka.listener-id}", topics = "${app.kafka.topic}", groupId = "${app.kafka.group-id}",
			autoStartup = "${app.kafka.auto-startup:true}")
	public void consumirNotificacion(ConsumerRecord<String, NotificacionDTO> record, Acknowledgment ack) {

		NotificacionDTO notificacion = record.value();
		if (notificacion == null || notificacion.usuario() == null) {
			// Mensaje vacío o sin el campo obligatorio: se informa al error handler (se registra y se omite)
			throw new IllegalArgumentException("Notificación sin contenido o sin usuario");
		}

		log.info("Mensaje consumido topic={} partition={} offset={} -> {}",
				record.topic(), record.partition(), record.offset(), notificacion);

		historial.registrar(new MensajeConsumido(notificacion, record.topic(), record.partition(), record.offset(),
				Instant.now().toString()));

		// Commit del offset solo después de procesar correctamente (AckMode MANUAL_IMMEDIATE)
		ack.acknowledge();

		log.info("Acknowledge realizado id={} partition={} offset={}",
				notificacion.id(), record.partition(), record.offset());
	}
}
