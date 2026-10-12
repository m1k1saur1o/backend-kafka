package com.example.consumer_service.service;

import java.util.List;
import java.util.Map;

import com.example.consumer_service.dto.EventoAuditoriaConsumido;

public interface HistorialAuditoriaService {

	/**
	 * Registra el evento si su eventId no se procesó antes.
	 *
	 * @return false si es un duplicado (reentrega de Kafka o reintento del productor)
	 */
	boolean registrarSiEsNuevo(EventoAuditoriaConsumido evento);

	/** Últimos eventos procesados (más reciente primero), filtrando por los criterios no nulos. */
	List<EventoAuditoriaConsumido> ultimos(int limite, String requestId, String canal, Integer status);

	long totalProcesados();

	long duplicadosDescartados();

	Map<String, Long> totalesPorCanal();

	Map<String, Long> totalesPorResultado();
}
