package com.example.consumer_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * El ConsumerFactory y el contenedor los crea Spring Boot a partir de spring.kafka.*
 * (ErrorHandlingDeserializer + JsonDeserializer, ack manual). Aquí se define qué hacer
 * cuando un mensaje no puede procesarse.
 */
@Configuration
public class KafkaConsumerConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

	@Bean
	CommonErrorHandler kafkaErrorHandler(KafkaAppProperties props) {

		// Reintenta N veces; si sigue fallando (o el JSON no es deserializable, que no se reintenta)
		// registra el error y confirma el offset para no bloquear la partición.
		DefaultErrorHandler handler = new DefaultErrorHandler(
				(record, ex) -> log.error("Mensaje descartado topic={} partition={} offset={}: {}",
						record.topic(), record.partition(), record.offset(),
						NestedExceptionUtils.getMostSpecificCause(ex).getMessage()),
				new FixedBackOff(props.retryBackoff().toMillis(), props.retryAttempts()));
		handler.setCommitRecovered(true);

		return handler;
	}
}
