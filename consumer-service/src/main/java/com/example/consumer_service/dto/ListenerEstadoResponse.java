package com.example.consumer_service.dto;

import java.util.List;

/**
 * estado: ACTIVO, PAUSANDO (pausa solicitada, pendiente del siguiente poll), PAUSADO o DETENIDO.
 */
public record ListenerEstadoResponse(
		String listenerId,
		String estado,
		boolean running,
		boolean pauseRequested,
		boolean paused,
		String groupId,
		List<String> particionesAsignadas) {
}
