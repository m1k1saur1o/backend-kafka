package com.example.auth_service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sin AUTH_CLIENT_ID el grant client_credentials queda deshabilitado.
 */
@SpringBootTest(properties = { "app.auth.client.id=", "app.auth.client.secret=" })
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:auth-service-test.properties")
class ClientCredentialsDeshabilitadoTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sinClienteConfiguradoDevuelve401() throws Exception {

		mockMvc.perform(post("/auth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("grant_type", "client_credentials")
				.param("client_id", "")
				.param("client_secret", ""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}
}
