package com.example.discovery_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Levanta el Eureka Server en un puerto aleatorio y verifica seguridad, health y el ciclo
 * registro -> consulta de una instancia (lo mismo que hacen los clientes Eureka).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:discovery-service-test.properties")
class DiscoveryServiceApplicationTests {

	@Autowired
	private TestRestTemplate rest;

	private TestRestTemplate autenticado() {
		return rest.withBasicAuth("test-eureka", "test-eureka-password-0123");
	}

	@Test
	void healthEsPublico() {

		ResponseEntity<String> r = rest.getForEntity("/actuator/health", String.class);

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(r.getBody()).contains("\"UP\"");
	}

	@Test
	void registroSinCredencialesDevuelve401() {

		assertThat(rest.getForEntity("/eureka/apps", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(rest.withBasicAuth("test-eureka", "incorrecta").getForEntity("/eureka/apps", String.class)
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void registraYDevuelveDosReplicasDeMsBackend() {

		registrar("ms-backend:10.0.0.11:8081", "10.0.0.11");
		registrar("ms-backend:10.0.0.12:8081", "10.0.0.12");

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
		ResponseEntity<String> r = autenticado().exchange("/eureka/apps/MS-BACKEND", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(r.getBody()).contains("ms-backend:10.0.0.11:8081", "ms-backend:10.0.0.12:8081");
	}

	private void registrar(String instanceId, String ip) {

		String json = """
				{"instance":{"instanceId":"%s","hostName":"%s","app":"MS-BACKEND","ipAddr":"%s",
				 "status":"UP","port":{"$":8081,"@enabled":"true"},
				 "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
				""".formatted(instanceId, ip, ip);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<Void> r = autenticado().exchange("/eureka/apps/MS-BACKEND", HttpMethod.POST,
				new HttpEntity<>(json, headers), Void.class);

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}
}
