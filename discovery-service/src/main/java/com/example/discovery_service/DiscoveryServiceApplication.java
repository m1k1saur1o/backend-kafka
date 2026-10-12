package com.example.discovery_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.PropertySource;

/**
 * Servidor Eureka. Registra dinámicamente ms-bff, ms-backend, auth-service, producer-service
 * y consumer-service; los clientes resuelven los nombres lógicos (por ejemplo
 * {@code http://ms-backend}) con Spring Cloud LoadBalancer. Kafka y Zookeeper no se registran.
 *
 * El registro y el dashboard exigen HTTP Basic (EUREKA_USERNAME / EUREKA_PASSWORD);
 * solo {@code /actuator/health} es público.
 */
@SpringBootApplication
@EnableEurekaServer
@ConfigurationPropertiesScan
@PropertySource("classpath:discovery-service.properties")
public class DiscoveryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiscoveryServiceApplication.class, args);
	}
}
