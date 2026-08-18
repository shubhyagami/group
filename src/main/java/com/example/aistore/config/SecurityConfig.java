package com.example.aistore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/**",
                                "/cart/**",
                                "/compare/**",
                                "/wishlist/**",
                                "/products/*/reviews",
                                "/profile/**",
                                "/checkout/**",
                                "/h2-console/**",
                                "/register",
                                "/verify-otp"))
                .headers(headers -> headers.frameOptions(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        // Public storefront & AI endpoints
                        .requestMatchers("/",
                                "/health",
                                "/api/chat/**",
                                "/api/telemetry/**",
                                "/api/recommendations/**",
                                "/api/compare/**",
                                "/api/search/**",
                                "/api/otp/**",
                                "/api/catalog/**",
                                "/products/**",
                                "/category/**",
                                "/search/**",
                                "/login",
                                "/register",
                                "/verify-otp",
                                "/api/auth/**",
                                "/h2-console/**").permitAll()
                        // Admin only
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // Authenticated users
                        .requestMatchers("/checkout/**", "/orders/**", "/profile/**",
                                "/wishlist/**", "/cart/**", "/api/orders/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(5)
                        .expiredUrl("/login?expired=true"));
        return http.build();
    }
}