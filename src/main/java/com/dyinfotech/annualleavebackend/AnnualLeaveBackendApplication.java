package com.dyinfotech.annualleavebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AnnualLeaveBackendApplication {

    public static void main(String[] args) {
    	SpringApplication.run(AnnualLeaveBackendApplication.class, args);
    }

}
