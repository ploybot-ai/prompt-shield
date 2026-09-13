package com.ploybot.promptshield.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class ProxyApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ProxyApplication.class);
        // The standalone proxy exists to serve the endpoints, which the library keeps off by default
        application.setDefaultProperties(Map.of("prompt-shield.proxy.enabled", "true"));
        application.run(args);
    }
}
