package com.ssafy.bapai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.ssafy.bapai.**.dao")
public class BapaiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BapaiApplication.class, args);
    }
}