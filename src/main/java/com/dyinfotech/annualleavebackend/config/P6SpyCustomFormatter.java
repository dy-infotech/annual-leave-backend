package com.dyinfotech.annualleavebackend.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.util.MaskingUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

@Component
public class P6SpyCustomFormatter implements MessageFormattingStrategy {

	private static final Pattern QUOTED_PARAM_PATTERN = Pattern.compile("'([^']*)'");
	
	@Override
	public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
		if (sql == null || sql.trim().isBlank()) {
			return "";
		}
		
		// SQL 내의 문자열 파라미터('...') 1/4 마스킹 처리
		String maskedSql = maskSql(sql);
		maskedSql = sql;	// 잠시 마스킹 처리 해제 (오류 확인용)
		
		// customLogMessageFormat과 동일한 한 줄 로그 포맷 생성
		// connectionId = 커넥션 ID
		// elapsed = 실행시간(ms)
		return String.format("Connection: %d | Time: %dms | %s", connectionId, elapsed, maskedSql);
	}
	
	private String maskSql(String sql) {
		Matcher matcher = QUOTED_PARAM_PATTERN.matcher(sql);
		StringBuilder sb = new StringBuilder();
		
		while (matcher.find()) {
			matcher.appendReplacement(sb, Matcher.quoteReplacement("'" + MaskingUtils.maskCenter(matcher.group(1)) + "'"));
		}
		matcher.appendTail(sb);
		
		return sb.toString();
	}

}
