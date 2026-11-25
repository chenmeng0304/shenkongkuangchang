package com.deepmine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeepMineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeepMineApplication.class, args);
    }
}
