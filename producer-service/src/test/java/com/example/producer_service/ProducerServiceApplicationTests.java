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
	private NewTopic topic;

	@Test
	void contextLoadsYDeclaraTopicoNotificaciones() {

		assertThat(topic.name()).isEqualTo("Notificaciones");
		assertThat(topic.numPartitions()).isEqualTo(3);
		assertThat(topic.replicationFactor()).isEqualTo((short) 3);
	}
}
