package com.jarvis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JarvisBackendApplication {
    public static void main(String[] args) {
        // Explicitly disable headless mode so java.awt.Robot can capture desktop screens
        System.setProperty("java.awt.headless", "false");
        SpringApplication app = new SpringApplication(JarvisBackendApplication.class);
        app.setHeadless(false);
        app.run(args);
    }
}
