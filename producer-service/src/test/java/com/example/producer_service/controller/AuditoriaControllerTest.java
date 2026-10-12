package com.example.producer_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
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

import com.example.producer_service.dto.BffCallEventDTO;
import com.example.producer_service.support.EventosAuditoria;
import com.example.producer_service.support.TestJwt;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * POST /auditoria/eventos: seguridad por rol AUDIT_PUBLISHER, validación del esquema v1 y
 * publicación en el tópico bff-call-events (nunca en Notificaciones).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:producer-service-test.properties")
@SuppressWarnings("unchecked")
class AuditoriaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	// Tipo crudo: reemplaza el único KafkaTemplate de Spring Boot, que comparten los publicadores
	// de notificaciones y de auditoría
	@SuppressWarnings("rawtypes")
	@MockBean(name = "kafkaTemplate")
	private KafkaTemplate kafkaTemplate;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void cerrarCircuito() {
		circuitBreakerRegistry.circuitBreaker("kafkaAuditoria").reset();
	}

	private MockHttpServletRequestBuilder publicar(String token, String body) {

		MockHttpServletRequestBuilder req = post("/auditoria/eventos").contentType(MediaType.APPLICATION_JSON)
				.content(body);
		return token == null ? req : req.header("Authorization", "Bearer " + token);
	}

	private void kafkaConfirma() {

		when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(inv -> {
			RecordMetadata md = new RecordMetadata(new TopicPartition("bff-call-events", 2), 9, 0, 0L, 0, 0);
			return CompletableFuture.completedFuture(
					new SendResult<>(new ProducerRecord<>("bff-call-events", inv.getArgument(1), inv.getArgument(2)), md));
		});
	}

	@Test
	void tokenDeServicioPublicaEnTopicoDeAuditoriaConClaveEventId() throws Exception {

		kafkaConfirma();

		mockMvc.perform(publicar(TestJwt.valido("AUDIT_PUBLISHER"), EventosAuditoria.json("evt-1", 200)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PUBLICADO"))
				.andExpect(jsonPath("$.eventId").value("evt-1"))
				.andExpect(jsonPath("$.topic").value("bff-call-events"))
				.andExpect(jsonPath("$.particion").value(2))
				.andExpect(jsonPath("$.offset").value(9));

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(kafkaTemplate).send(eq("bff-call-events"), eq("evt-1"), captor.capture());
		BffCallEventDTO evento = (BffCallEventDTO) captor.getValue();
		assertThat(evento.channel()).isEqualTo("movil");
		assertThat(evento.status()).isEqualTo(200);
		verify(kafkaTemplate, never()).send(eq("Notificaciones"), anyString(), any());
	}

	@Test
	void eventoDeErrorFuncionalTambienSePublica() throws Exception {

		kafkaConfirma();

		mockMvc.perform(publicar(TestJwt.valido("AUDIT_PUBLISHER"), EventosAuditoria.json("evt-404", 404)))
				.andExpect(status().isCreated());

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(kafkaTemplate).send(eq("bff-call-events"), eq("evt-404"), captor.capture());
		assertThat(((BffCallEventDTO) captor.getValue()).outcome()).isEqualTo("CLIENT_ERROR");
	}

	@Test
	void tokenDeUsuarioSinRolAuditPublisherDevuelve403() throws Exception {

		mockMvc.perform(publicar(TestJwt.valido("USER", "ADMIN"), EventosAuditoria.json("evt-2", 200)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("insufficient_scope"));

		verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
	}

	@Test
	void sinTokenDevuelve401() throws Exception {

		mockMvc.perform(publicar(null, EventosAuditoria.json("evt-3", 200)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_token"));
	}

	@Test
	void eventoSinEventIdOVersionNoSoportadaDevuelve400() throws Exception {

		mockMvc.perform(publicar(TestJwt.valido("AUDIT_PUBLISHER"),
				EventosAuditoria.json("", 200)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.eventId").exists());

		mockMvc.perform(publicar(TestJwt.valido("AUDIT_PUBLISHER"),
				EventosAuditoria.json("evt-4", 200).replace("\"1.0\"", "\"2.0\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.schemaVersion").exists());

		verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
	}

	@Test
	void kafkaNoDisponibleReintentaYDevuelve503() throws Exception {

		when(kafkaTemplate.send(anyString(), anyString(), any()))
				.thenReturn(CompletableFuture.failedFuture(new KafkaException("broker caído")));

		mockMvc.perform(publicar(TestJwt.valido("AUDIT_PUBLISHER"), EventosAuditoria.json("evt-5", 200)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("kafka_unavailable"));

		// Reintento con el mismo evento (misma clave eventId)
		verify(kafkaTemplate, times(2)).send(eq("bff-call-events"), eq("evt-5"), any());
	}
}
