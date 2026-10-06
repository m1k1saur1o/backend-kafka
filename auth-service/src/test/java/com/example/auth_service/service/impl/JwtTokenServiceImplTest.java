package com.example.auth_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.example.auth_service.config.JwtProperties;
import com.example.auth_service.dto.TokenResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceImplTest {

	private static final String SECRET = "unit-test-secret-0123456789abcdefghijkl";
	private static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private final JwtProperties props = new JwtProperties(SECRET, "issuer-x", "aud-x", Duration.ofMinutes(5));
	private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();

	private final UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
			"ana", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

	@Test
	void incluyeClaimsObligatorios() {

		JwtTokenServiceImpl service = new JwtTokenServiceImpl(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), props);

		TokenResponse response = service.emitirToken(auth);
		Jwt jwt = decoder.decode(response.accessToken());

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(300);
		assertThat(jwt.getSubject()).isEqualTo("ana");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("issuer-x");
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void tokenEmitidoEnElPasadoQuedaExpirado() {

		Clock haceUnaHora = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
		JwtTokenServiceImpl service = new JwtTokenServiceImpl(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), props,
				haceUnaHora);

		String token = service.emitirToken(auth).accessToken();

		assertThatThrownBy(() -> decoder.decode(token))
				.isInstanceOf(JwtValidationException.class)
				.hasMessageContaining("expired");
	}
}
