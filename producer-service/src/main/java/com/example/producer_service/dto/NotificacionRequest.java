package com.example.producer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /notificaciones. El id y la fecha los genera el servicio.
 */
public record NotificacionRequest(
		@NotBlank(message = "usuario es obligatorio") @Size(max = 100, message = "usuario admite hasta 100 caracteres") String usuario) {
}
