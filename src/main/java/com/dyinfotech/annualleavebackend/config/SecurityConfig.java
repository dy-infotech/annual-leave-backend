package com.dyinfotech.annualleavebackend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.dyinfotech.annualleavebackend.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;
	
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {
        http
                // JWT는 세션을 안 쓰므로 CSRF 보호 불필요
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())   // 아래 CorsConfigurationSource 빈을 사용
                // 세션을 아예 생성하지 않도록 설정
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                		// Swagger/OpenAPI는 application.yml에서 기본 비활성화한다.
                		// 명시적으로 활성화한 개발/사내망 환경에서만 이 경로를 공개한다.
                		.requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // signin/refresh/logout 등 인증 lifecycle은 HttpOnly refresh cookie와
                        // X-SSO-Refresh 헤더로 보호하므로 access JWT가 없어도 접근할 수 있다.
                        .requestMatchers("/api/auth/**").permitAll()

                        // JWT role은 로그인 시점 snapshot이므로 최종 권한 근거로 사용하지 않는다.
                        // /api/admin/**의 현재 권한은 AdminAuthorizationInterceptor가 중앙 검증한다.
                        .requestMatchers("/api/admin/**").authenticated()

                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )

                // UsernamePasswordAuthenticationFilter보다 먼저 JWT 필터 실행
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));                   // Authorization, Content-Type 포함
        config.setExposedHeaders(List.of("Authorization"));       // 응답에서 토큰 헤더 읽어야 하면
        config.setAllowCredentials(true);                         // 공통 HttpOnly refresh cookie 사용
        config.setMaxAge(3600L);                                  // 프리플라이트 캐시(초)
     
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}