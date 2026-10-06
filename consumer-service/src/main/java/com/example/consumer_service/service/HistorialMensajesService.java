package com.example.consumer_service.service;

import java.util.List;

import com.example.consumer_service.dto.MensajeConsumido;

public interface HistorialMensajesService {

	void registrar(MensajeConsumido mensaje);

	/** Últimos mensajes procesados, del más reciente al más antiguo. */
	List<MensajeConsumido> ultimos(int limite);

	long totalProcesados();
}
