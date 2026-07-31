package com.dyinfotech.annualleavebackend.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MaskingUtils {
	public static String maskEmail(String email) {
	    if (email == null || email.isBlank()) {
	        return email;
	    }

	    int at = email.indexOf('@');
	    if (at < 0) {
	        return email;
	    }

	    String local = email.substring(0, at);
	    String domain = email.substring(at);

	    int visible;

	    if (local.length() >= 8) {
	        visible = 4;
	    } else if (local.length() >= 4) {
	        visible = 2;
	    } else if (local.length() >= 2) {
	        visible = 1;
	    } else {
	        visible = 0;
	    }

	    return local.substring(0, visible)
	            + "*".repeat(local.length() - visible)
	            + domain;
	}
	
	public static String maskCenter(String str) {
		if (str == null || str.isBlank()) {
			return str;
		}
		
		int len = str.length();
		
		// 1글자: 'Y', 'N', 'M', 'F' 등 단순 코드값인 경우가 많으므로 그대로 노출
	    if (len == 1) {
	        return str;
	    }

	    // 2글자: '이산', '김철' 등 2자 성명을 고려하여 뒷글자 마스킹 -> '이*'
	    if (len == 2) {
	        return str.substring(0, 1) + "*";
	    }
		
		int quarter = len / 4;
		
		// 3글자 (예: '홍길동'): quarter가 0이므로 가운데 마스킹 -> '홍*동'
		if (quarter == 0) {
			return str.substring(0, 1) + "*" + str.substring(len - 1);
		}
		
		int headLen = quarter;
		int tailLen = quarter;
		int maskLen = len - headLen - tailLen;
		
		return str.substring(0, headLen) + "*".repeat(maskLen) + str.substring(len - tailLen);
	}
}
