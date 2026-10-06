package com.example.auth_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:auth-service-test.properties")
class AuthControllerTest {

	private static final String SECRET = "test-secret-solo-para-pruebas-0123456789abcdef";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void loginValidoDevuelveJwtFirmadoConClaimsEsperados() throws Exception {

		String body = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.expires_in").value(600))
				.andReturn().getResponse().getContentAsString();

		JsonNode json = objectMapper.readTree(body);
		String token = json.get("access_token").asText();

		NimbusJwtDecoder decoder = NimbusJwtDecoder
				.withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		Jwt jwt = decoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo("test-user");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
		assertThat(jwt.getAudience()).containsExactly("test-audience");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "ADMIN");
		assertThat(jwt.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
		assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(600));
		assertThat(jwt.getId()).isNotBlank();
	}

	@Test
	void passwordIncorrectaDevuelve401() throws Exception {

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test-user\",\"password\":\"incorrecta\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_grant"))
				.andExpect(jsonPath("$.access_token").doesNotExist());
	}

	@Test
	void usuarioInexistenteDevuelve401() throws Exception {

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"nadie\",\"password\":\"test-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void cuerpoIncompletoDevuelve400ConDetalle() throws Exception {

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test-user\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"))
				.andExpect(jsonPath("$.details.password").exists());
	}

	@Test
	void jsonMalformadoDevuelve400() throws Exception {

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void otrosEndpointsNoEstanExpuestos() throws Exception {

		mockMvc.perform(get("/auth/usuarios")).andExpect(status().isUnauthorized());
	}

	@Test
	void healthEsPublico() throws Exception {

		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
