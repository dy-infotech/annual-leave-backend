package com.dyinfotech.annualleavebackend.common;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Forwarding header는 백엔드와 직접 연결된 신뢰 프록시에서 온 요청에만 사용한다.
     * 현재 운영은 동일 호스트 reverse proxy를 전제로 loopback peer만 신뢰한다.
     * 그 외 peer가 임의로 보낸 X-Forwarded-For/X-Real-IP는 무시한다.
     */
    public static String resolve(HttpServletRequest request) {
        String peer = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(peer)) {
            return peer;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (isUsable(forwardedFor)) {
            String firstHop = forwardedFor.split(",")[0].trim();
            if (!firstHop.isBlank()) {
                return firstHop;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (isUsable(realIp)) {
            return realIp.trim();
        }

        return peer;
    }

    private static boolean isTrustedProxy(String peer) {
        return "127.0.0.1".equals(peer)
                || "::1".equals(peer)
                || "0:0:0:0:0:0:0:1".equals(peer);
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
