package com.ntg.CitizenLink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CitizenLinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(CitizenLinkApplication.class, args);
	}

}
