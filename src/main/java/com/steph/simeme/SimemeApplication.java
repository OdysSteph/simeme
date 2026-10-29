package com.steph.simeme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimemeApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimemeApplication.class, args);
	}

}
