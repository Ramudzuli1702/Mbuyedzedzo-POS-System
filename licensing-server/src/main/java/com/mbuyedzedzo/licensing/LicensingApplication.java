package com.mbuyedzedzo.licensing;

import com.mbuyedzedzo.licensing.config.EmailProperties;
import com.mbuyedzedzo.licensing.config.LicensingProperties;
import com.mbuyedzedzo.licensing.config.PayFastProperties;
import com.mbuyedzedzo.licensing.config.PricingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({LicensingProperties.class, PayFastProperties.class, PricingProperties.class, EmailProperties.class})
@EnableScheduling
public class LicensingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicensingApplication.class, args);
    }
}
