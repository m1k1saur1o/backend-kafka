package com.example.consumer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.consumer_service.service.HistorialAuditoriaService;
import com.example.consumer_service.support.TestJwt;

/**
 * Con un broker Kafka embebido: consumo en tiempo real de eventos de auditoría (éxito y error),
 * descarte de duplicados por eventId, descarte de mensajes corruptos o de versión no soportada sin
 * bloquear la partición, commit de offsets y consulta por el endpoint protegido.
 */
@SpringBootTest(properties = {
		"app.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"app.kafka.auto-startup=true",
		"app.kafka.retry-attempts=0",
		"app.kafka.auditoria.group-id=grupo-auditoria-integracion" })
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:consumer-service-test.properties")
@EmbeddedKafka(partitions = 1, topics = { "bff-call-events", "Notificaciones" })
class AuditoriaConsumoIntegrationTest {

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private HistorialAuditoriaService historial;

	@Autowired
	private MockMvc mockMvc;

	private static String evento(String eventId, String requestId, int status, String outcome, String version) {
		return """
				{"schemaVersion":"%s","eventType":"BFF_HTTP_CALL","eventId":"%s","timestamp":"2026-10-11T12:00:00Z",
				 "requestId":"%s","sourceService":"ms-bff","method":"GET","path":"/api/cajeros/cuentas/99999",
				 "routePattern":"/api/cajeros/cuentas/{cuentaId}","endpoint":"cajeros.cuentas","channel":"cajeros",
				 "user":"ana","authenticated":true,"status":%d,"outcome":"%s","durationMs":15,
				 "errorCode":"DATOS_NO_ENCONTRADOS","errorMessage":"No se encontraron registros","campoFuturo":"x"}
				""".formatted(version, eventId, requestId, status, outcome);
	}

	@Test
	void consumeEventosDeduplicaYExponeHistorialProtegido() throws Exception {

		KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));

		template.send("bff-call-events", "x", "{no es json").get();                                     // offset 0
		template.send("bff-call-events", "e1", evento("e1", "req-1", 404, "CLIENT_ERROR", "1.0")).get(); // 1
		template.send("bff-call-events", "e1", evento("e1", "req-1", 404, "CLIENT_ERROR", "1.0")).get(); // 2 duplicado
		template.send("bff-call-events", "e2", evento("e2", "req-2", 9, "X", "2.0")).get();              // 3 versión no soportada
		template.send("bff-call-events", "e3", evento("e3", "req-3", 200, "SUCCESS", "1.1")).get();      // 4

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(historial.totalProcesados()).isEqualTo(2);
			assertThat(historial.duplicadosDescartados()).isEqualTo(1);
		});
		assertThat(historial.ultimos(10, null, null, null))
				.extracting(e -> e.evento().eventId()).containsExactly("e3", "e1");

		// Offset 5 confirmado: todos los mensajes (válidos, duplicado, corrupto e inválido) quedaron committed
		try (AdminClient admin = AdminClient.create(
				Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
			await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
				Map<TopicPartition, OffsetAndMetadata> offsets = admin
						.listConsumerGroupOffsets("grupo-auditoria-integracion").partitionsToOffsetAndMetadata().get();
				assertThat(offsets.get(new TopicPartition("bff-call-events", 0)).offset()).isEqualTo(5L);
			});
		}

		mockMvc.perform(get("/auditoria/eventos").param("requestId", "req-1")
				.header("Authorization", "Bearer " + TestJwt.valido("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalProcesados").value(2))
				.andExpect(jsonPath("$.duplicadosDescartados").value(1))
				.andExpect(jsonPath("$.porCanal.cajeros").value(2))
				.andExpect(jsonPath("$.porResultado.CLIENT_ERROR").value(1))
				.andExpect(jsonPath("$.eventos.length()").value(1))
				.andExpect(jsonPath("$.eventos[0].evento.status").value(404))
				.andExpect(jsonPath("$.eventos[0].evento.errorCode").value("DATOS_NO_ENCONTRADOS"))
				.andExpect(jsonPath("$.eventos[0].topic").value("bff-call-events"));

		mockMvc.perform(get("/auditoria/eventos")).andExpect(status().isUnauthorized());
	}
}
