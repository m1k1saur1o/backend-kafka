package com.example.producer_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:producer-service-test.properties")
class ProducerServiceApplicationTests {

	@Autowired
	private NewTopic topicNotificaciones;

	@Autowired
	private NewTopic topicAuditoriaBff;

	@Test
	void contextLoadsYDeclaraTopicoNotificaciones() {

		assertThat(topicNotificaciones.name()).isEqualTo("Notificaciones");
		assertThat(topicNotificaciones.numPartitions()).isEqualTo(3);
		assertThat(topicNotificaciones.replicationFactor()).isEqualTo((short) 3);
	}

	@Test
	void declaraTopicoDeAuditoriaSeparadoConRetencionDeSieteDias() {

		assertThat(topicAuditoriaBff.name()).isEqualTo("bff-call-events");
		assertThat(topicAuditoriaBff.numPartitions()).isEqualTo(3);
		assertThat(topicAuditoriaBff.replicationFactor()).isEqualTo((short) 3);
		assertThat(topicAuditoriaBff.configs())
				.containsEntry("retention.ms", "604800000")
				.containsEntry("min.insync.replicas", "2");
	}
}
