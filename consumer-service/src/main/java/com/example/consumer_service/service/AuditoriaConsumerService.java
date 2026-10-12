package com.example.consumer_service.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

import com.example.consumer_service.dto.BffCallEventDTO;

public interface AuditoriaConsumerService {

	void consumirEvento(ConsumerRecord<String, BffCallEventDTO> record, Acknowledgment ack);
}
