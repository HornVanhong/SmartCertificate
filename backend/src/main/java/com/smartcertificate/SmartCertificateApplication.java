package com.smartcertificate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartCertificateApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCertificateApplication.class, args);
    }
}
