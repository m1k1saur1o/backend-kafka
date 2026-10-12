package com.example.producer_service.service;

import com.example.producer_service.dto.AuditoriaPublicacionResponse;
import com.example.producer_service.dto.BffCallEventDTO;

public interface AuditoriaProducerService {

	AuditoriaPublicacionResponse publicarEvento(BffCallEventDTO evento);
}
