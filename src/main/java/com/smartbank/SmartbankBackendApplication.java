package com.smartbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartbankBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartbankBackendApplication.class, args);
		System.err.println("=========Application started==========");
	}

}
