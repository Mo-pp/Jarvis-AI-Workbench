package com.msz.resume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiResumeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiResumeGeneratorApplication.class, args);
    }

}
