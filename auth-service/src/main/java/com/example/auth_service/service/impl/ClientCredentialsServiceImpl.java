package com.example.auth_service.service.impl;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.auth_service.config.ServiceClientProperties;
import com.example.auth_service.exception.ClienteInvalidoException;
import com.example.auth_service.service.ClientCredentialsService;

/**
 * Valida el cliente de servicio configurado por entorno. El secreto se guarda solo como
 * hash y la comparación se hace siempre con el PasswordEncoder (también cuando el client_id
 * no coincide) para no revelar por tiempo de respuesta qué parte falló.
 */
@Service
public class ClientCredentialsServiceImpl implements ClientCredentialsService {

	private final ServiceClientProperties props;
	private final PasswordEncoder encoder;
	private final String secretCodificado;
	private final String hashSenuelo;

	public ClientCredentialsServiceImpl(ServiceClientProperties props, PasswordEncoder encoder) {
		this.props = props;
		this.encoder = encoder;
		this.hashSenuelo = encoder.encode("senuelo-para-tiempo-constante");
		this.secretCodificado = !props.habilitado() ? null
				: props.secret().startsWith("{") ? props.secret() : encoder.encode(props.secret());
	}

	@Override
	public Authentication autenticarCliente(String clientId, String clientSecret) {

		boolean idCoincide = props.habilitado() && props.id().equals(clientId);
		boolean secretCoincide = encoder.matches(clientSecret == null ? "" : clientSecret,
				idCoincide ? secretCodificado : hashSenuelo);

		if (!idCoincide || !secretCoincide) {
			throw new ClienteInvalidoException();
		}

		List<SimpleGrantedAuthority> authorities = props.roles().stream()
				.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
				.toList();
		return UsernamePasswordAuthenticationToken.authenticated(props.id(), null, authorities);
	}
}
