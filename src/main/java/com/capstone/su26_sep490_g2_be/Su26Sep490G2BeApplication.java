package com.capstone.su26_sep490_g2_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Entry point — chạy qua SpringApplication.run bên dưới.
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Su26Sep490G2BeApplication {

	public static void main(String[] args) {
		SpringApplication.run(Su26Sep490G2BeApplication.class, args);
	}

}
