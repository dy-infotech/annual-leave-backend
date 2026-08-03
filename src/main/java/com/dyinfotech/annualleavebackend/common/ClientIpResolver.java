package com.dyinfotech.annualleavebackend.common;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpResolver {

    private static final String[] HEADERS = {
        "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "X-Real-IP"
    };

    public static String resolve(HttpServletRequest request) {
        for (String header : HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For는 "client, proxy1, proxy2" 형태일 수 있음
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
