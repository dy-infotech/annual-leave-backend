package com.dyinfotech.annualleavebackend.filter;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dyinfotech.annualleavebackend.common.ClientIpResolver;
import com.dyinfotech.annualleavebackend.common.IpContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class IpFilter extends OncePerRequestFilter {
    private static final String MDC_KEY = "clientIp";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = ClientIpResolver.resolve(request);
        IpContext.set(ip);
        MDC.put(MDC_KEY, ip);

        try {
            filterChain.doFilter(request, response);
        } finally {
            IpContext.clear();
            MDC.remove(MDC_KEY);
        }
    }
    
//	// 제외 경로 필요할 경우 고려
//	@Override
//	protected boolean shouldNotFilter(HttpServletRequest request) {
//		String path = request.getRequestURI();
//		return path.startsWith("/actuator") || path.equals("/health");
//	}
}
