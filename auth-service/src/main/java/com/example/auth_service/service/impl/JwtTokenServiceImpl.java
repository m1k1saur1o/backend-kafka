package com.example.auth_service.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.auth_service.config.JwtProperties;
import com.example.auth_service.dto.TokenResponse;
import com.example.auth_service.service.TokenService;

/**
 * Emite JWT firmados con HS256. Claims: sub, roles, iat, nbf, exp, iss, aud y jti.
 */
@Service
public class JwtTokenServiceImpl implements TokenService {

	public static final String ROLES_CLAIM = "roles";
	private static final String ROLE_PREFIX = "ROLE_";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties props;
	private final Clock clock;

	@Autowired
	public JwtTokenServiceImpl(JwtEncoder jwtEncoder, JwtProperties props) {
		this(jwtEncoder, props, Clock.systemUTC());
	}

	JwtTokenServiceImpl(JwtEncoder jwtEncoder, JwtProperties props, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.props = props;
		this.clock = clock;
	}

	@Override
	public TokenResponse emitirToken(Authentication authentication) {

		Instant ahora = clock.instant();
		Instant expiracion = ahora.plus(props.expiration());

		List<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
				.toList();

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.id(UUID.randomUUID().toString())
				.issuer(props.issuer())
				.audience(List.of(props.audience()))
				.subject(authentication.getName())
				.issuedAt(ahora)
				.notBefore(ahora)
				.expiresAt(expiracion)
				.claim(ROLES_CLAIM, roles)
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		return new TokenResponse(token, "Bearer", props.expiration().toSeconds());
	}
}
