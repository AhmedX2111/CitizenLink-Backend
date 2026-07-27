package com.ntg.CitizenLink;

import com.ntg.CitizenLink.security.config.CorsProperties;
import com.ntg.CitizenLink.security.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class CitizenLinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(CitizenLinkApplication.class, args);
	}

}
