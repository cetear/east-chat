package com.easychat.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.easychat")
public class EasyChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyChatApplication.class, args);
    }
}
