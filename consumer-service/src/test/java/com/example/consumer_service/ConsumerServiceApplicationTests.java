package com.example.consumer_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:consumer-service-test.properties")
class ConsumerServiceApplicationTests {

	@Autowired
	private KafkaListenerEndpointRegistry registry;

	@Test
	void contextLoadsYRegistraElListener() {

		assertThat(registry.getListenerContainer("notificacionListener")).isNotNull();
		assertThat(registry.getListenerContainer("notificacionListener").getGroupId())
				.isEqualTo("notificaciones-consumer");
	}
}
