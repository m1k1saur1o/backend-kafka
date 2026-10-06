package com.example.consumer_service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.consumer_service.support.TestJwt;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:consumer-service-test.properties")
class AdminEndpointsSecurityTest {

	private static final String ESTADO = "/kafka-listener/notificacionListener/estado";
	private static final String PAUSAR = "/kafka-listener/notificacionListener/pausar";

	@Autowired
	private MockMvc mockMvc;

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	@Test
	void sinTokenDevuelve401() throws Exception {

		mockMvc.perform(get(ESTADO)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_token"));
		mockMvc.perform(post(PAUSAR)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/notificaciones/consumidas")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenExpiradoDevuelve401() throws Exception {

		String expirado = TestJwt.token(TestJwt.SECRET, TestJwt.ISSUER, TestJwt.AUDIENCE,
				Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), "ADMIN");

		mockMvc.perform(get(ESTADO).header("Authorization", bearer(expirado))).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioPuedeConsultarEstado() throws Exception {

		mockMvc.perform(get(ESTADO).header("Authorization", bearer(TestJwt.valido("USER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listenerId").value("notificacionListener"))
				.andExpect(jsonPath("$.estado").value("DETENIDO"))
				.andExpect(jsonPath("$.groupId").value("notificaciones-consumer"));
	}

	@Test
	void usuarioSinRolAdminNoPuedePausar() throws Exception {

		mockMvc.perform(post(PAUSAR).header("Authorization", bearer(TestJwt.valido("USER"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("insufficient_scope"));
	}

	@Test
	void adminPuedePausar() throws Exception {

		mockMvc.perform(post(PAUSAR).header("Authorization", bearer(TestJwt.valido("ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pauseRequested").value(true));
	}

	@Test
	void listenerInexistenteDevuelve404() throws Exception {

		mockMvc.perform(get("/kafka-listener/noExiste/estado").header("Authorization", bearer(TestJwt.valido("USER"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void historialRequiereTokenYResponde() throws Exception {

		mockMvc.perform(get("/notificaciones/consumidas").header("Authorization", bearer(TestJwt.valido("USER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalProcesados").isNumber());
	}

	@Test
	void healthEsPublico() throws Exception {

		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
