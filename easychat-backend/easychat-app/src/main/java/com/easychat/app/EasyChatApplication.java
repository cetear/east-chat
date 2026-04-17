package com.easychat.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.easychat")
@EnableScheduling
public class EasyChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyChatApplication.class, args);
    }
}
