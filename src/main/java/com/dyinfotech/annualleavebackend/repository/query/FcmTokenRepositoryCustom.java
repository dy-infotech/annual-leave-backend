package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.FcmToken;

public interface FcmTokenRepositoryCustom {
	List<FcmToken> findInactiveTokensBatch(LocalDateTime threshold, Long afterTokenId, int limit);
	int updateTokenAndTouch(Long employeeId, String deviceOs, LocalDateTime now, String token);
	int updateTokenAndTouchIfOwner(
			Long expectedEmployeeId,
			Long employeeId,
			String deviceOs,
			LocalDateTime now,
			String token);
	int updateTokenAndTouchIfBinding(
			Long expectedEmployeeId,
			String expectedAuthSessionMarker,
			Long employeeId,
			String authSessionMarker,
			String deviceOs,
			LocalDateTime now,
			String token);
	long deleteByTokenAndEmployeeId(String token, Long employeeId);
	long deleteByTokenAndBinding(
			String token,
			Long employeeId,
			String authSessionMarker);
	void deleteByUpdatedAtBefore(LocalDateTime threshold);
}
