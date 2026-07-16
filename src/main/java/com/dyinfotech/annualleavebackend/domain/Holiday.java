package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday implements Persistable<LocalDate> {

    @Id
    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "name", length = 50)
    private String name;
    
    // 영속성 상태를 추적하기 위한 가상 필드 (DB 저장 안 됨)
    @Transient
    private boolean isNew = true;
    
    @Builder
    public Holiday(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }

	@Override
	public LocalDate getId() {
		// TODO Auto-generated method stub
		return this.holidayDate;
	}
	
	@Override
    public boolean isNew() {
        return this.isNew; // true면 무조건 persist()가 호출됨
    }
	
	// DB에서 조회되거나(PostLoad), 이미 저장된(PostPersist) 후에는 New가 아님을 표시
    @PostLoad
    @PostPersist
    public void markNotNew() {
        this.isNew = false;
    }
}
