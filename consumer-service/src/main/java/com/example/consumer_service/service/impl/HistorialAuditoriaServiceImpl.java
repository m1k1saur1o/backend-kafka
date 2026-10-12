package com.example.consumer_service.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.example.consumer_service.config.AuditoriaKafkaProperties;
import com.example.consumer_service.dto.EventoAuditoriaConsumido;
import com.example.consumer_service.service.HistorialAuditoriaService;

/**
 * Historial acotado en memoria (no persistente) de los eventos de auditoría procesados por esta
 * instancia, con deduplicación por eventId sobre una ventana de los últimos 10 × history-size ids.
 */
@Service
public class HistorialAuditoriaServiceImpl implements HistorialAuditoriaService {

	private final Deque<EventoAuditoriaConsumido> eventos = new ConcurrentLinkedDeque<>();
	private final Set<String> idsVistos;
	private final int capacidad;
	private final int capacidadIds;
	private final AtomicLong total = new AtomicLong();
	private final AtomicLong duplicados = new AtomicLong();
	private final Map<String, AtomicLong> porCanal = new ConcurrentHashMap<>();
	private final Map<String, AtomicLong> porResultado = new ConcurrentHashMap<>();

	public HistorialAuditoriaServiceImpl(AuditoriaKafkaProperties props) {
		this.capacidad = props.historySize();
		this.capacidadIds = props.historySize() * 10;
		this.idsVistos = Collections.synchronizedSet(new LinkedHashSet<>());
	}

	@Override
	public boolean registrarSiEsNuevo(EventoAuditoriaConsumido consumido) {

		String eventId = consumido.evento().eventId();
		synchronized (idsVistos) {
			if (!idsVistos.add(eventId)) {
				duplicados.incrementAndGet();
				return false;
			}
			while (idsVistos.size() > capacidadIds) {
				idsVistos.remove(idsVistos.iterator().next());
			}
		}

		eventos.addFirst(consumido);
		total.incrementAndGet();
		porCanal.computeIfAbsent(consumido.evento().channel(), k -> new AtomicLong()).incrementAndGet();
		porResultado.computeIfAbsent(consumido.evento().outcome(), k -> new AtomicLong()).incrementAndGet();
		while (eventos.size() > capacidad) {
			eventos.pollLast();
		}
		return true;
	}

	@Override
	public List<EventoAuditoriaConsumido> ultimos(int limite, String requestId, String canal, Integer status) {

		List<EventoAuditoriaConsumido> resultado = new ArrayList<>();
		for (EventoAuditoriaConsumido e : eventos) {
			if (resultado.size() >= limite) {
				break;
			}
			if ((requestId == null || requestId.equals(e.evento().requestId()))
					&& (canal == null || canal.equalsIgnoreCase(e.evento().channel()))
					&& (status == null || status == e.evento().status())) {
				resultado.add(e);
			}
		}
		return resultado;
	}

	@Override
	public long totalProcesados() {
		return total.get();
	}

	@Override
	public long duplicadosDescartados() {
		return duplicados.get();
	}

	@Override
	public Map<String, Long> totalesPorCanal() {
		return copia(porCanal);
	}

	@Override
	public Map<String, Long> totalesPorResultado() {
		return copia(porResultado);
	}

	private static Map<String, Long> copia(Map<String, AtomicLong> origen) {

		Map<String, Long> copia = new LinkedHashMap<>();
		new TreeMap<>(origen).forEach((k, v) -> copia.put(k, v.get()));
		return copia;
	}
}
