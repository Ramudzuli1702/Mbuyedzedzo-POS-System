package com.mbuyedzedzo.licensing;

import com.mbuyedzedzo.licensing.config.LicensingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(LicensingProperties.class)
@EnableScheduling
public class LicensingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicensingApplication.class, args);
    }
}
