package com.example.consumer_service.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

import com.example.consumer_service.dto.NotificacionDTO;

public interface KafkaConsumerService {

	void consumirNotificacion(ConsumerRecord<String, NotificacionDTO> record, Acknowledgment ack);
}
