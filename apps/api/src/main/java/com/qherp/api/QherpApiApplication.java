package com.qherp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class QherpApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(QherpApiApplication.class, args);
	}

}
