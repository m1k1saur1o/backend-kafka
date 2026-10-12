package com.example.producer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.producer_service.support.EventosAuditoria;
import com.example.producer_service.support.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Con un broker Kafka embebido: el evento recibido por HTTP queda en bff-call-events como JSON
 * sin cabeceras de tipo Java, con clave = eventId y con acks=all + idempotencia del productor.
 */
@SpringBootTest(properties = {
		"app.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"app.kafka.replicas=1",
		"spring.kafka.admin.auto-create=true" })
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:producer-service-test.properties")
@EmbeddedKafka(partitions = 1, topics = "bff-call-events")
class AuditoriaKafkaIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void eventoPublicadoQuedaEnElTopicoComoJsonConClaveEventId() throws Exception {

		mockMvc.perform(post("/auditoria/eventos")
				.header("Authorization", "Bearer " + TestJwt.valido("AUDIT_PUBLISHER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(EventosAuditoria.json("evt-integracion", 503)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.topic").value("bff-call-events"));

		Map<String, Object> props = KafkaTestUtils.consumerProps("verificador", "false", broker);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
				new StringDeserializer(), new StringDeserializer()).createConsumer()) {

			broker.consumeFromAnEmbeddedTopic(consumer, "bff-call-events");
			ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "bff-call-events",
					Duration.ofSeconds(15));

			assertThat(record.key()).isEqualTo("evt-integracion");
			assertThat(record.headers().lastHeader("__TypeId__")).isNull();

			JsonNode json = objectMapper.readTree(record.value());
			assertThat(json.get("schemaVersion").asText()).isEqualTo("1.0");
			assertThat(json.get("eventType").asText()).isEqualTo("BFF_HTTP_CALL");
			assertThat(json.get("status").asInt()).isEqualTo(503);
			assertThat(json.get("outcome").asText()).isEqualTo("SERVER_ERROR");
			assertThat(json.get("channel").asText()).isEqualTo("movil");
		}
	}
}
