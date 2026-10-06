package com.example.auth_service.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import com.example.auth_service.config.AuthUserProperties;
import com.example.auth_service.config.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		http
				.csrf(AbstractHttpConfigurer::disable) // API stateless, sin cookies de sesión
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {

		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(AuthUserProperties props, PasswordEncoder encoder) {

		// Si la contraseña ya viene codificada ({bcrypt}..., {argon2}...) se usa tal cual;
		// si viene en texto plano se cifra con BCrypt en memoria y nunca se registra en logs.
		String password = props.password().startsWith("{") ? props.password() : encoder.encode(props.password());

		return new InMemoryUserDetailsManager(User.withUsername(props.username())
				.password(password)
				.roles(props.roles().stream().map(String::trim).map(String::toUpperCase).toArray(String[]::new))
				.build());
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {

		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(encoder);
		provider.setUserDetailsService(userDetailsService);

		return new ProviderManager(provider);
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties props) {

		SecretKey key = new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}
}
