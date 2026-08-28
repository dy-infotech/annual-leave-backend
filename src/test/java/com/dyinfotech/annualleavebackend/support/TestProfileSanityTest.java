package com.dyinfotech.annualleavebackend.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트가 개발 DB가 아닌 테스트 DB에 연결되는지 확인하는 안전장치.
 *
 * 이 테스트가 실패하면 다른 통합 테스트가 개발 데이터를 건드릴 수 있으므로
 * 원인을 먼저 해결해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestProfileSanityTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void 연결된_DB는_테스트_DB여야_한다() throws Exception {
		try (var conn = dataSource.getConnection()) {
			String url = conn.getMetaData().getURL();
			assertTrue(url.contains("annual_leave_test"),
					"통합 테스트가 테스트 DB가 아닌 곳에 연결되었습니다: " + url);
		}
	}
}
