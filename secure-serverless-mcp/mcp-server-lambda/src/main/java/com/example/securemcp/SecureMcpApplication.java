package com.example.securemcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecureMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureMcpApplication.class, args);
    }
}
