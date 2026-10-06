package com.example.producer_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * El ProducerFactory y el KafkaTemplate los crea Spring Boot a partir de spring.kafka.*
 * (variables de entorno). Aquí solo se declara el tópico: KafkaAdmin lo crea si no existe
 * cuando spring.kafka.admin.auto-create=true (KAFKA_TOPIC_AUTO_CREATE).
 */
@Configuration
public class KafkaProducerConfig {

	@Bean
	NewTopic topicNotificaciones(KafkaAppProperties props) {

		return TopicBuilder.name(props.topic())
				.partitions(props.partitions())
				.replicas(props.replicas())
				.config("retention.ms", "43200000") // 12 horas
				.config("min.insync.replicas", String.valueOf(Math.min(2, props.replicas())))
				.build();
	}
}
