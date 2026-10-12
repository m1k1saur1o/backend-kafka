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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.TokenResponse;
import com.example.auth_service.exception.GrantNoSoportadoException;
import com.example.auth_service.service.ClientCredentialsService;
import com.example.auth_service.service.TokenService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;

/**
 * Endpoint de obtención de tokens. Cumple el rol de "token endpoint": entrega un
 * access token JWT (Bearer, RFC 6750) que producer-service y consumer-service
 * validan como OAuth2 Resource Servers (firma, issuer, audience y expiración).
 *
 * El login está protegido con un Rate Limiter de Resilience4j (instancia "login") que
 * limita los intentos por ventana de tiempo para frenar ataques de fuerza bruta; al
 * superarse el límite se responde 429 (ver GlobalExceptionHandler).
 *
 * {@code POST /auth/token} emite tokens de servicio (client_credentials) con su propio
 * Rate Limiter (instancia "clientToken").
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthenticationManager authenticationManager;
	private final TokenService tokenService;
	private final ClientCredentialsService clientCredentialsService;

	public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
			ClientCredentialsService clientCredentialsService) {
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
		this.clientCredentialsService = clientCredentialsService;
	}

	@RateLimiter(name = "login")
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

	/**
	 * Grant client_credentials (RFC 6749 §4.4) para llamadas entre servicios, por ejemplo
	 * ms-bff publicando eventos de auditoría en producer-service. El token resultante lleva
	 * {@code sub=<client_id>} y los roles del cliente (por defecto AUDIT_PUBLISHER).
	 */
	@RateLimiter(name = "clientToken")
	@PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokenResponse> token(@RequestParam("grant_type") String grantType,
			@RequestParam("client_id") String clientId, @RequestParam("client_secret") String clientSecret) {

		if (!"client_credentials".equals(grantType)) {
			throw new GrantNoSoportadoException();
		}

		Authentication cliente = clientCredentialsService.autenticarCliente(clientId, clientSecret);

		log.info("Token de servicio emitido para el cliente '{}'", cliente.getName());

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header("Pragma", "no-cache")
				.body(tokenService.emitirToken(cliente));
	}
}
