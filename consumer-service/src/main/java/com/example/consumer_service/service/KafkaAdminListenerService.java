package com.example.consumer_service.service;

import java.util.List;

import com.example.consumer_service.dto.ListenerEstadoResponse;

public interface KafkaAdminListenerService {

	ListenerEstadoResponse pausarListener(String id);

	ListenerEstadoResponse reanudarListener(String id);

	ListenerEstadoResponse obtenerEstadoListener(String id);

	List<ListenerEstadoResponse> listarListeners();
}
