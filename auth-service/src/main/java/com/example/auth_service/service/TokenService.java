package com.example.auth_service.service;

import org.springframework.security.core.Authentication;

import com.example.auth_service.dto.TokenResponse;

public interface TokenService {

	TokenResponse emitirToken(Authentication authentication);
}
