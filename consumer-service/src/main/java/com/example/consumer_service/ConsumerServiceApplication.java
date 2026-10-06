package com.example.consumer_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.PropertySource;

/**
 * Consume notificaciones desde Kafka. Los endpoints HTTP administrativos están protegidos
 * como OAuth2 Resource Server (JWT Bearer emitido por auth-service).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@PropertySource("classpath:consumer-service.properties")
public class ConsumerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsumerServiceApplication.class, args);
	}
}
