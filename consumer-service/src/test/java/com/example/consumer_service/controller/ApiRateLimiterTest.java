package com.example.consumer_service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.consumer_service.support.TestJwt;

/**
 * Verifica el Rate Limiter de Resilience4j sobre la API HTTP con un límite reducido.
 */
@SpringBootTest(properties = {
		"resilience4j.ratelimiter.instances.consumerApi.limit-for-period=2",
		"resilience4j.ratelimiter.instances.consumerApi.limit-refresh-period=60s" })
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:consumer-service-test.properties")
class ApiRateLimiterTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void superarElLimiteDevuelve429() throws Exception {

		String bearer = "Bearer " + TestJwt.valido("USER");

		mockMvc.perform(get("/notificaciones/consumidas").header("Authorization", bearer)).andExpect(status().isOk());
		mockMvc.perform(get("/kafka-listener").header("Authorization", bearer)).andExpect(status().isOk());

		mockMvc.perform(get("/notificaciones/consumidas").header("Authorization", bearer))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value("too_many_requests"));
	}
}
