package com.example.auth_service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.TokenResponse;
import com.example.auth_service.service.TokenService;

import jakarta.validation.Valid;

/**
 * Endpoint de obtención de tokens. Cumple el rol de "token endpoint": entrega un
 * access token JWT (Bearer, RFC 6750) que producer-service y consumer-service
 * validan como OAuth2 Resource Servers (firma, issuer, audience y expiración).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthenticationManager authenticationManager;
	private final TokenService tokenService;

	public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {

		// Lanza AuthenticationException (-> 401) si las credenciales no son válidas
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

		log.info("Token emitido para el usuario '{}'", authentication.getName());

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header("Pragma", "no-cache")
				.body(tokenService.emitirToken(authentication));
	}
}
