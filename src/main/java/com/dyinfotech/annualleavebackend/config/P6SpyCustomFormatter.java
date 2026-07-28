package com.dyinfotech.annualleavebackend.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

@Component
public class P6SpyCustomFormatter implements MessageFormattingStrategy {

	private static final Pattern QUOTED_PARAM_PATTERN = Pattern.compile("'([^']*)'");
	
	@Override
	public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
		if (sql == null || sql.trim().isEmpty()) {
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
			String originalValue = matcher.group(1);
			String maskedValue = maskValue(originalValue);
			
			matcher.appendReplacement(sb, Matcher.quoteReplacement("'" + maskedValue + "'"));
		}
		matcher.appendTail(sb);
		
		return sb.toString();
	}
	
	private String maskValue(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		
		int len = str.length();
		
		// 1글자: 'Y', 'N', 'M', 'F' 등 단순 코드값인 경우가 많으므로 그대로 노출
	    if (len == 1) {
	        return str;
	    }

	    // 2글자: '이산', '김철' 등 2자 성명을 고려하여 뒷글자 마스킹 -> '이*'
	    if (len == 2) {
	        return str.charAt(0) + "*";
	    }
		
		int quarter = len / 4;
		
		// 3글자 (예: '홍길동'): quarter가 0이므로 가운데 마스킹 -> '홍*동'
		if (quarter == 0) {
			return str.charAt(0) + "*" + str.charAt(len - 1);
		}
		
		int headLen = quarter;
		int tailLen = quarter;
		int maskLen = len - headLen - tailLen;
		
		return str.substring(0, headLen) + "*".repeat(maskLen) + str.substring(len - tailLen);
	}

}
