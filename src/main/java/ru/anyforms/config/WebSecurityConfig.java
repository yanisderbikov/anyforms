package ru.anyforms.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    @Value("${allowed.origins}")
    private List<String> allowedOrigins;

    public WebSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    // ── Межсервисные ручки: общий секрет SERVICE_JWT_TOKEN ──
                    .requestMatchers("/api/actuator/**").hasRole("SERVICE")
                    .requestMatchers("/api/tech/**").hasRole("SERVICE")
                    .requestMatchers("/api/pusher/**").hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/api/auth/register-admin").hasRole("SERVICE")
                    // ── Пользовательские роли ──
                    .requestMatchers("/api/orders/**").hasAnyRole("ADMIN", "SALES_MANAGER", "PROJECT_MANAGER")
                    .requestMatchers("/api/custom-product-items/**").hasAnyRole("ADMIN", "SALES_MANAGER", "PROJECT_MANAGER")
                    .requestMatchers("/api/custom-product-files/**").hasAnyRole("ADMIN", "SALES_MANAGER", "PROJECT_MANAGER")
                    .requestMatchers("/api/product/create").hasRole("ADMIN")
                    .requestMatchers("/api/product/all").hasRole("ADMIN")
                    .requestMatchers("/api/product/*/photos").hasRole("ADMIN")
                    .requestMatchers("/api/product/*/photos/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/product/*").hasRole("ADMIN")
                    .requestMatchers("/api/amo/**").hasRole("ADMIN")
                    .requestMatchers("/api/income-export/**").hasAnyRole("ADMIN", "SERVICE")
                    .requestMatchers("/api/invoice/**").hasAnyRole("ADMIN", "SALES_MANAGER")
                    .requestMatchers("/api/training-invoice/**").hasAnyRole("ADMIN", "SALES_MANAGER")
                    .requestMatchers("/api/receipt/**").hasRole("ADMIN")
                    .requestMatchers("/api/promo-code/**").hasRole("ADMIN")
                    .requestMatchers("/webhook/**").permitAll()
                    .requestMatchers("/api/**").permitAll()
                    .anyRequest().permitAll()
            )
            // Актуатор и пушер исторически отвечают 401, остальное — 403 по умолчанию
            // (на 403 завязан редирект на логин в админке фронта)
            .exceptionHandling(e -> e
                    .defaultAuthenticationEntryPointFor(unauthorizedEntryPoint(),
                            new AntPathRequestMatcher("/api/actuator/**"))
                    .defaultAuthenticationEntryPointFor(unauthorizedEntryPoint(),
                            new AntPathRequestMatcher("/api/pusher/**")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"invalid auth token\"}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins); // Разрешить все origin
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")); // Разрешить все методы
        configuration.setAllowedHeaders(List.of("*")); // Разрешить все заголовки
        configuration.setExposedHeaders(List.of("*")); // Разрешить все заголовки в ответе
        configuration.setAllowCredentials(false); // Не требуется для разрешения всех origin

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
