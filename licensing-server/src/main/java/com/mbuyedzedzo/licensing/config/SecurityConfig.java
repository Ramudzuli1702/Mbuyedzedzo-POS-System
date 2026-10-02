package com.mbuyedzedzo.licensing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig {

    /** The desktop API: no session, no CSRF, open — the license KEY is the credential. */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * PayFast's ITN: no session, no CSRF (it's a server-to-server POST from
     * PayFast, which can't carry a CSRF token — the ITN's own signature +
     * validate-postback are what authenticate it instead, in PayFastWebhookController).
     */
    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain webhookChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/webhooks/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /** The admin PMS — form login, roles. */
    @Bean
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/img/**",
                        "/favicon.ico", "/actuator/health",
                        "/", "/pricing", "/buy/**", "/account/set-password").permitAll()
                .requestMatchers("/admin/agents/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/api/**").allowedMethods("*");
            }
        };
    }
}
