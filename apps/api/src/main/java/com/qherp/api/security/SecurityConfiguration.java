package com.qherp.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler, SessionCurrentUserFilter sessionCurrentUserFilter)
			throws Exception {
		return http
			.csrf((csrf) -> csrf.csrfTokenRepository(csrfTokenRepository()))
			.formLogin((formLogin) -> formLogin.disable())
			.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/login").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.addFilterBefore(sessionCurrentUserFilter,
					org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
			.httpBasic((httpBasic) -> httpBasic.disable())
			.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
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
