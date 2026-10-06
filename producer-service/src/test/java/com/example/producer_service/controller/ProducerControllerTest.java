package com.example.producer_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.producer_service.dto.NotificacionDTO;
import com.example.producer_service.support.TestJwt;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:producer-service-test.properties")
class ProducerControllerTest {

	private static final String BODY = "{\"usuario\":\"ana\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private KafkaTemplate<String, NotificacionDTO> kafkaTemplate;

	private MockHttpServletRequestBuilder publicar(String token, String body) {

		MockHttpServletRequestBuilder req = post("/notificaciones").contentType(MediaType.APPLICATION_JSON).content(body);
		return token == null ? req : req.header("Authorization", "Bearer " + token);
	}

	@SuppressWarnings("unchecked")
	private void kafkaConfirma() {

		when(kafkaTemplate.send(anyString(), anyString(), any(NotificacionDTO.class))).thenAnswer(inv -> {
			NotificacionDTO dto = inv.getArgument(2);
			RecordMetadata md = new RecordMetadata(new TopicPartition("Notificaciones", 1), 41, 0, 0L, 0, 0);
			return CompletableFuture.completedFuture(
					new SendResult<>(new ProducerRecord<>("Notificaciones", dto.id(), dto), md));
		});
	}

	@Test
	void tokenValidoPublicaYDevuelve201() throws Exception {

		kafkaConfirma();

		mockMvc.perform(publicar(TestJwt.valido("USER"), BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PUBLICADA"))
				.andExpect(jsonPath("$.notificacion.usuario").value("ana"))
				.andExpect(jsonPath("$.notificacion.id").isNotEmpty())
				.andExpect(jsonPath("$.notificacion.fecha").isNotEmpty())
				.andExpect(jsonPath("$.topic").value("Notificaciones"))
				.andExpect(jsonPath("$.particion").value(1))
				.andExpect(jsonPath("$.offset").value(41));

		ArgumentCaptor<NotificacionDTO> captor = ArgumentCaptor.forClass(NotificacionDTO.class);
		verify(kafkaTemplate).send(eq("Notificaciones"), anyString(), captor.capture());
		assertThat(captor.getValue().usuario()).isEqualTo("ana");
		assertThat(Instant.parse(captor.getValue().fecha())).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void sinTokenDevuelve401() throws Exception {

		mockMvc.perform(publicar(null, BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.error").value("invalid_token"));

		verify(kafkaTemplate, never()).send(anyString(), anyString(), any(NotificacionDTO.class));
	}

	@Test
	void tokenMalformadoDevuelve401() throws Exception {

		mockMvc.perform(publicar("esto-no-es-un-jwt", BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void firmaConOtroSecretoDevuelve401() throws Exception {

		String token = TestJwt.token("otro-secreto-distinto-0123456789abcdefgh", TestJwt.ISSUER, TestJwt.AUDIENCE,
				Instant.now(), Instant.now().plusSeconds(600), "USER");

		mockMvc.perform(publicar(token, BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenExpiradoDevuelve401() throws Exception {

		String token = TestJwt.token(TestJwt.SECRET, TestJwt.ISSUER, TestJwt.AUDIENCE,
				Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), "USER");

		mockMvc.perform(publicar(token, BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("expired")));
	}

	@Test
	void issuerIncorrectoDevuelve401() throws Exception {

		String token = TestJwt.token(TestJwt.SECRET, "otro-issuer", TestJwt.AUDIENCE,
				Instant.now(), Instant.now().plusSeconds(600), "USER");

		mockMvc.perform(publicar(token, BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void audienceIncorrectaDevuelve401() throws Exception {

		String token = TestJwt.token(TestJwt.SECRET, TestJwt.ISSUER, "otra-api",
				Instant.now(), Instant.now().plusSeconds(600), "USER");

		mockMvc.perform(publicar(token, BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSinRolesDevuelve403() throws Exception {

		mockMvc.perform(publicar(TestJwt.valido(), BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("insufficient_scope"));
	}

	@Test
	void cuerpoIncompletoDevuelve400() throws Exception {

		mockMvc.perform(publicar(TestJwt.valido("USER"), "{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.usuario").exists());
	}

	@Test
	void jsonMalformadoDevuelve400() throws Exception {

		mockMvc.perform(publicar(TestJwt.valido("USER"), "{\"usuario\":"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void kafkaNoDisponibleDevuelve503() throws Exception {

		when(kafkaTemplate.send(anyString(), anyString(), any(NotificacionDTO.class)))
				.thenReturn(CompletableFuture.failedFuture(new KafkaException("broker caído")));

		mockMvc.perform(publicar(TestJwt.valido("USER"), BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("kafka_unavailable"));
	}

	@Test
	void healthEsPublico() throws Exception {

		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
