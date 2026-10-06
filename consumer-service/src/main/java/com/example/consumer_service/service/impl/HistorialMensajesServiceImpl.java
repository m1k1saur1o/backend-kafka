package com.example.consumer_service.service.impl;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.example.consumer_service.config.KafkaAppProperties;
import com.example.consumer_service.dto.MensajeConsumido;
import com.example.consumer_service.service.HistorialMensajesService;

/**
 * Historial acotado en memoria (no persistente) para verificar el consumo vía HTTP.
 */
@Service
public class HistorialMensajesServiceImpl implements HistorialMensajesService {

	private final Deque<MensajeConsumido> mensajes = new ConcurrentLinkedDeque<>();
	private final AtomicLong total = new AtomicLong();
	private final int capacidad;

	public HistorialMensajesServiceImpl(KafkaAppProperties props) {
		this.capacidad = props.historySize();
	}

	@Override
	public void registrar(MensajeConsumido mensaje) {

		mensajes.addFirst(mensaje);
		total.incrementAndGet();
		while (mensajes.size() > capacidad) {
			mensajes.pollLast();
		}
	}

	@Override
	public List<MensajeConsumido> ultimos(int limite) {

		List<MensajeConsumido> resultado = new ArrayList<>();
		for (MensajeConsumido m : mensajes) {
			if (resultado.size() >= limite) {
				break;
			}
			resultado.add(m);
		}
		return resultado;
	}

	@Override
	public long totalProcesados() {
		return total.get();
	}
}
