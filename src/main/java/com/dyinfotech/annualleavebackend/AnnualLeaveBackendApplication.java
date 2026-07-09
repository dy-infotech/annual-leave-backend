package com.dyinfotech.annualleavebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class AnnualLeaveBackendApplication {

    public static void main(String[] args) {
    	// .env file load
    	Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    	dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        
    	SpringApplication.run(AnnualLeaveBackendApplication.class, args);
    }

}
