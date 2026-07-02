package com.dyinfotech.annualleavebackend.dto;

import lombok.Builder;
import lombok.Getter;

public class EmployeeDto {

    @Getter
    @Builder
    public static class EmployResponse {

        private String employeeNo;
        private String name;
        private String position;
        private String department;
    }
}
