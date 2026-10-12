package com.example.discovery_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

/**
 * Sin EUREKA_USERNAME / EUREKA_PASSWORD el servidor no debe arrancar.
 */
class CredencialesObligatoriasTest {

	@Test
	void sinCredencialesNoArranca() {

		SpringApplication app = new SpringApplication(DiscoveryServiceApplication.class);

		assertThatThrownBy(() -> app.run("--app.eureka.username=", "--app.eureka.password=",
				"--server.port=0", "--spring.main.banner-mode=off"))
				.hasRootCauseInstanceOf(BindValidationException.class)
				.hasStackTraceContaining("app.eureka");
	}
}
