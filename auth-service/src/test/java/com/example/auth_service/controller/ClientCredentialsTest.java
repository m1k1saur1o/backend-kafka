package com.example.auth_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Grant client_credentials de POST /auth/token (token de servicio usado por ms-bff).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:auth-service-test.properties")
class ClientCredentialsTest {

	private static final String SECRET = "test-secret-solo-para-pruebas-0123456789abcdef";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private static MockHttpServletRequestBuilder token(String grantType, String clientId, String clientSecret) {

		return post("/auth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", grantType)
				.param("client_id", clientId)
				.param("client_secret", clientSecret);
	}

	@Test
	void clienteValidoObtieneTokenDeServicioConRolAuditPublisher() throws Exception {

		String body = mockMvc.perform(token("client_credentials", "test-client", "test-client-secret"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.expires_in").value(600))
				.andReturn().getResponse().getContentAsString();

		Jwt jwt = NimbusJwtDecoder
				.withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
				.macAlgorithm(MacAlgorithm.HS256).build()
				.decode(objectMapper.readTree(body).get("access_token").asText());

		assertThat(jwt.getSubject()).isEqualTo("test-client");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("AUDIT_PUBLISHER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
		assertThat(jwt.getAudience()).containsExactly("test-audience", "test-bff-audience");
	}

	@Test
	void secretoIncorrectoDevuelve401InvalidClient() throws Exception {

		mockMvc.perform(token("client_credentials", "test-client", "otro-secreto"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"))
				.andExpect(jsonPath("$.access_token").doesNotExist());
	}

	@Test
	void clienteInexistenteDevuelve401() throws Exception {

		mockMvc.perform(token("client_credentials", "nadie", "test-client-secret"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	@Test
	void credencialesDeUsuarioNoSirvenComoCliente() throws Exception {

		mockMvc.perform(token("client_credentials", "test-user", "test-password"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void credencialesDeClienteNoSirvenEnLogin() throws Exception {

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test-client\",\"password\":\"test-client-secret\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void grantNoSoportadoDevuelve400() throws Exception {

		mockMvc.perform(token("password", "test-client", "test-client-secret"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unsupported_grant_type"));
	}

	@Test
	void parametroFaltanteDevuelve400() throws Exception {

		mockMvc.perform(post("/auth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", "client_credentials"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}
}
