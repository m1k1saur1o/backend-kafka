package com.example.producer_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.PropertySource;

/**
 * Publica notificaciones en el tópico Kafka configurado. Los endpoints HTTP están
 * protegidos como OAuth2 Resource Server: exigen un JWT Bearer emitido por auth-service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@PropertySource("classpath:producer-service.properties")
public class ProducerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProducerServiceApplication.class, args);
	}
}
