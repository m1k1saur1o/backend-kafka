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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Verifica el Rate Limiter de Resilience4j sobre /auth/login con un límite reducido.
 */
@SpringBootTest(properties = {
		"resilience4j.ratelimiter.instances.login.limit-for-period=2",
		"resilience4j.ratelimiter.instances.login.limit-refresh-period=60s" })
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:auth-service-test.properties")
class LoginRateLimiterTest {

	@Autowired
	private MockMvc mockMvc;

	private static MockHttpServletRequestBuilder login(String password) {

		return post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test-user\",\"password\":\"" + password + "\"}");
	}

	@Test
	void superarElLimiteDeIntentosDevuelve429() throws Exception {

		mockMvc.perform(login("incorrecta")).andExpect(status().isUnauthorized());
		mockMvc.perform(login("incorrecta")).andExpect(status().isUnauthorized());

		// Incluso con la contraseña correcta, el tercer intento de la ventana se rechaza
		mockMvc.perform(login("test-password"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value("too_many_requests"))
				.andExpect(jsonPath("$.access_token").doesNotExist());
	}
}
