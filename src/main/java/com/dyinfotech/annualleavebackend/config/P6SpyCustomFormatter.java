package com.dyinfotech.annualleavebackend.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.util.MaskingUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
		
		if (elapsed > 500) {
		    log.warn("Slow SQL detected: {}ms | {}", elapsed, maskedSql);
		}
		
		// category = JDBC Event Type, elapsed = execution time(ms)
		return String.format("Category: %s | Time: %dms | %s", category, elapsed, maskedSql);
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
