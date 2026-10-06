package com.example.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.PropertySource;

/**
 * Servicio de autenticación: valida usuario/contraseña y emite un JWT firmado
 * que los demás microservicios validan actuando como OAuth2 Resource Servers.
 *
 * La configuración no sensible versionada vive en {@code auth-service.properties}
 * (solo placeholders de variables de entorno). {@code application.properties} /
 * {@code application.yml} quedan reservados para overrides locales no versionados.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@PropertySource("classpath:auth-service.properties")
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}
}
