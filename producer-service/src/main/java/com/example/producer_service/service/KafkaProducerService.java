package com.example.producer_service.service;

import com.example.producer_service.dto.NotificacionRequest;
import com.example.producer_service.dto.PublicacionResponse;

public interface KafkaProducerService {

	PublicacionResponse enviarNotificacion(NotificacionRequest request);
}
