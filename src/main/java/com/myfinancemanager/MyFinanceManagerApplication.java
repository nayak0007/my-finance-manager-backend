package com.myfinancemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class MyFinanceManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyFinanceManagerApplication.class, args);
    }
}
