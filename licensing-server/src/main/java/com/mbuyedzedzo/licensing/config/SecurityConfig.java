package com.mbuyedzedzo.licensing.config;

import com.mbuyedzedzo.licensing.admin.AdminUserDetailsService;
import com.mbuyedzedzo.licensing.commerce.CustomerUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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

    /**
     * The customer self-service portal — its own form login, entirely separate
     * session/principal type from the admin one. Matched ahead of webChain
     * (which has no securityMatcher and so runs last, as the catch-all) so
     * /account/** never falls through to the admin login.
     */
    @Bean
    @org.springframework.core.annotation.Order(3)
    SecurityFilterChain accountChain(HttpSecurity http, CustomerUserDetailsService uds,
                                      PasswordEncoder encoder) throws Exception {
        http.securityMatcher("/account/**")
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/account/login", "/account/set-password").permitAll()
                    .anyRequest().authenticated())
            .authenticationProvider(daoProvider(uds, encoder))
            .formLogin(f -> f
                .loginPage("/account/login")
                .loginProcessingUrl("/account/login")
                .defaultSuccessUrl("/account", true)
                .failureUrl("/account/login?error")
                .permitAll())
            .logout(l -> l
                .logoutUrl("/account/logout")
                .logoutSuccessUrl("/account/login?logout")
                .permitAll());
        return http.build();
    }

    /** The admin PMS — form login, roles. */
    @Bean
    SecurityFilterChain webChain(HttpSecurity http, AdminUserDetailsService uds,
                                 PasswordEncoder encoder) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/img/**",
                        "/favicon.ico", "/actuator/health",
                        "/", "/pricing", "/download", "/testers", "/buy/**", "/request-license").permitAll()
                .requestMatchers("/admin/agents/**", "/admin/settings/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated())
            .authenticationProvider(daoProvider(uds, encoder))
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

    /**
     * Each chain gets its own explicit provider pinned to the right
     * UserDetailsService — with three UserDetailsService beans now (admin,
     * customer, plus Spring Boot's default), the shared auto-configured
     * AuthenticationManager can't pick the right one on its own.
     */
    private static DaoAuthenticationProvider daoProvider(
            org.springframework.security.core.userdetails.UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
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
