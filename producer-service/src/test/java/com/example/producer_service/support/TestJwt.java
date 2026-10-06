package com.example.producer_service.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Genera JWT equivalentes a los de auth-service para las pruebas.
 */
public final class TestJwt {

	public static final String SECRET = "test-secret-solo-para-pruebas-0123456789abcdef";
	public static final String ISSUER = "test-issuer";
	public static final String AUDIENCE = "test-audience";

	private TestJwt() {
	}

	public static String valido(String... roles) {
		return token(SECRET, ISSUER, AUDIENCE, Instant.now(), Instant.now().plusSeconds(600), roles);
	}

	public static String token(String secret, String issuer, String audience, Instant iat, Instant exp,
			String... roles) {

		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
				new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.audience(List.of(audience))
				.subject("test-user")
				.issuedAt(iat)
				.expiresAt(exp)
				.claim("roles", List.of(roles))
				.build();

		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}
