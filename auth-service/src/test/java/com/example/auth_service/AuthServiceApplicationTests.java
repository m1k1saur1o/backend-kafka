package com.example.auth_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:auth-service-test.properties")
class AuthServiceApplicationTests {

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void contextLoads() {
		assertThat(jwtEncoder).isNotNull();
	}
}
