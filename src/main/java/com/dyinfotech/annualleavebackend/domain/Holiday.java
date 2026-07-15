package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "name", length = 50)
    private String name;
    
    @Builder
    public Holiday(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }
}
