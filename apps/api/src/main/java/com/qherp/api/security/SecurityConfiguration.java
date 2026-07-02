package com.qherp.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler, SessionCurrentUserFilter sessionCurrentUserFilter)
			throws Exception {
		return http
			.csrf((csrf) -> csrf.disable())
			.formLogin((formLogin) -> formLogin.disable())
			.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/api/health", "/api/auth/login").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.addFilterBefore(sessionCurrentUserFilter,
					org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
			.httpBasic(Customizer.withDefaults())
			.build();
	}

	@Bean
	SessionCurrentUserFilter sessionCurrentUserFilter(CurrentUserService currentUserService) {
		return new SessionCurrentUserFilter(currentUserService);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
