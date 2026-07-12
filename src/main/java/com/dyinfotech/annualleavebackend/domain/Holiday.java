package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "holiday")
@Getter
@IdClass(Holiday.HolidayId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday {

    @Column(name = "name", length = 50)
    private String name;

    @Id
    @Column(name = "year", length = 4)
    private String year;

    @Id
    @Column(name = "month", length = 2)
    private String month;

    @Id
    @Column(name = "day", length = 2)
    private String day;
    
    @Builder
    public Holiday(String name, String year, String month, String day) {
        this.name = name;
        this.year = year;
        this.month = month;
        this.day = day;
    }
    

    // Composite id defined as a static inner class so Holiday is self-contained.
    @Getter
    public static class HolidayId implements java.io.Serializable {
        private String year;
        private String month;
        private String day;

        public HolidayId() {
        }

        public HolidayId(String year, String month, String day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HolidayId that = (HolidayId) o;
            return java.util.Objects.equals(year, that.year) && 
            		java.util.Objects.equals(month, that.month) && 
            		java.util.Objects.equals(day, that.day);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(year, month, day);
        }
    }
    
}
