package com.example.consumer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import com.example.consumer_service.dto.MensajeConsumido;
import com.example.consumer_service.service.HistorialMensajesService;

/**
 * Verifica con un broker Kafka embebido: deserialización JSON, consumo, commit del offset
 * después del ack y que un mensaje corrupto no bloquea la partición.
 */
@SpringBootTest(properties = {
		"app.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"app.kafka.auto-startup=true",
		"app.kafka.retry-attempts=0",
		"app.kafka.group-id=grupo-integracion" })
@TestPropertySource(locations = "classpath:consumer-service-test.properties")
@EmbeddedKafka(partitions = 1, topics = "Notificaciones")
class KafkaConsumoIntegrationTest {

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private HistorialMensajesService historial;

	@Test
	void consumeJsonConfirmaOffsetYDescartaMensajesCorruptos() throws Exception {

		KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));

		template.send("Notificaciones", "x", "{esto no es json").get();
		template.send("Notificaciones", "abc",
				"{\"id\":\"abc\",\"fecha\":\"2026-01-01T00:00:00Z\",\"usuario\":\"ana\"}").get();

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(historial.ultimos(10))
				.extracting(MensajeConsumido::notificacion)
				.anySatisfy(n -> {
					assertThat(n.id()).isEqualTo("abc");
					assertThat(n.usuario()).isEqualTo("ana");
				}));

		MensajeConsumido consumido = historial.ultimos(1).get(0);
		assertThat(consumido.offset()).isEqualTo(1L);

		// Offset 2 confirmado = ambos mensajes (corrupto descartado + válido procesado) quedaron committed
		try (AdminClient admin = AdminClient.create(
				Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
			await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
				Map<TopicPartition, OffsetAndMetadata> offsets = admin
						.listConsumerGroupOffsets("grupo-integracion").partitionsToOffsetAndMetadata().get();
				assertThat(offsets.get(new TopicPartition("Notificaciones", 0)).offset()).isEqualTo(2L);
			});
		}
	}
}
