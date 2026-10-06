package com.example.consumer_service.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
		String error,
		@JsonProperty("error_description") String errorDescription,
		Map<String, String> details) {

	public ErrorResponse(String error, String errorDescription) {
		this(error, errorDescription, Map.of());
	}
}
