package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;

public interface FcmTokenRepositoryCustom {
	int updateTokenAndTouch(Long employeeId, String deviceOs, LocalDateTime now, String token);
}
