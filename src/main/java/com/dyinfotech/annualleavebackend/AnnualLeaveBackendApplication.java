package com.dyinfotech.annualleavebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AnnualLeaveBackendApplication extends SpringBootServletInitializer {

	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(AnnualLeaveBackendApplication.class);
    }
	
    public static void main(String[] args) {
    	java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "");
    	SpringApplication.run(AnnualLeaveBackendApplication.class, args);
    }

}
