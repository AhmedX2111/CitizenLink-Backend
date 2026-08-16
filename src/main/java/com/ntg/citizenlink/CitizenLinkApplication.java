package com.ntg.citizenlink;

import com.ntg.citizenlink.security.config.CorsProperties;
import com.ntg.citizenlink.security.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
@EnableScheduling
public class CitizenLinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(CitizenLinkApplication.class, args);
	}

}
