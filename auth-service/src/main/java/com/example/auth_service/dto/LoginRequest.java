package com.example.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "username es obligatorio") @Size(max = 100) String username,
		@NotBlank(message = "password es obligatorio") @Size(max = 200) String password) {

	@Override
	public String toString() {
		return "LoginRequest[username=" + username + ", password=****]";
	}
}
