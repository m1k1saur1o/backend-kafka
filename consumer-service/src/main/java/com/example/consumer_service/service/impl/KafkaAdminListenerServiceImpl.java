package com.example.consumer_service.service.impl;

import java.util.List;
import java.util.Optional;

import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import com.example.consumer_service.dto.ListenerEstadoResponse;
import com.example.consumer_service.exception.ListenerNoEncontradoException;
import com.example.consumer_service.service.KafkaAdminListenerService;

@Service
public class KafkaAdminListenerServiceImpl implements KafkaAdminListenerService {

	private final KafkaListenerEndpointRegistry registry;

	public KafkaAdminListenerServiceImpl(KafkaListenerEndpointRegistry registry) {
		this.registry = registry;
	}

	@Override
	public ListenerEstadoResponse pausarListener(String id) {

		MessageListenerContainer container = container(id);
		container.pause();
		return estado(id, container);
	}

	@Override
	public ListenerEstadoResponse reanudarListener(String id) {

		MessageListenerContainer container = container(id);
		if (!container.isRunning()) {
			container.start();
		}
		container.resume();
		return estado(id, container);
	}

	@Override
	public ListenerEstadoResponse obtenerEstadoListener(String id) {

		return estado(id, container(id));
	}

	@Override
	public List<ListenerEstadoResponse> listarListeners() {

		return registry.getListenerContainerIds().stream().sorted()
				.map(id -> estado(id, registry.getListenerContainer(id)))
				.toList();
	}

	private MessageListenerContainer container(String id) {

		return Optional.ofNullable(registry.getListenerContainer(id))
				.orElseThrow(() -> new ListenerNoEncontradoException(id));
	}

	private ListenerEstadoResponse estado(String id, MessageListenerContainer c) {

		String estado;
		if (!c.isRunning()) {
			estado = "DETENIDO";
		} else if (c.isContainerPaused()) {
			estado = "PAUSADO";
		} else if (c.isPauseRequested()) {
			estado = "PAUSANDO";
		} else {
			estado = "ACTIVO";
		}

		List<String> particiones = Optional.ofNullable(c.getAssignedPartitions()).orElse(List.of()).stream()
				.map(TopicPartition::toString).sorted().toList();

		return new ListenerEstadoResponse(id, estado, c.isRunning(), c.isPauseRequested(), c.isContainerPaused(),
				c.getGroupId(), particiones);
	}
}
