package com.mvelelo.licensing;

import com.mvelelo.licensing.config.LicensingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LicensingProperties.class)
public class LicensingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicensingApplication.class, args);
    }
}
