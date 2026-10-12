package com.example.consumer_service.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.example.consumer_service.dto.BffCallEventDTO;

/**
 * Fábrica de contenedores exclusiva del tópico de auditoría. Parte de la misma configuración
 * spring.kafka.* (ErrorHandlingDeserializer + JsonDeserializer, auto-commit desactivado, ack
 * manual, seguridad SASL, concurrencia y CommonErrorHandler) y solo cambia el tipo por defecto
 * del JSON a {@link BffCallEventDTO}. El ConsumerFactory no se expone como bean para no
 * reemplazar el que Spring Boot crea para el listener de Notificaciones.
 */
@Configuration
public class AuditoriaKafkaConsumerConfig {

	public static final String CONTAINER_FACTORY = "auditoriaListenerContainerFactory";

	@Bean(CONTAINER_FACTORY)
	ConcurrentKafkaListenerContainerFactory<Object, Object> auditoriaListenerContainerFactory(
			ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties kafkaProperties,
			ObjectProvider<SslBundles> sslBundles, AuditoriaKafkaProperties auditoria) {

		Map<String, Object> props = kafkaProperties.buildConsumerProperties(sslBundles.getIfAvailable());
		props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BffCallEventDTO.class.getName());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, auditoria.groupId());

		ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
		configurer.configure(factory, new DefaultKafkaConsumerFactory<>(props));
		return factory;
	}
}
