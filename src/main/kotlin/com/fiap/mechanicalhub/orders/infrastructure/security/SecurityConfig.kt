package com.fiap.mechanicalhub.orders.infrastructure.security

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        gatewayAuthenticationFilter: GatewayAuthenticationFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    // Cliente: autenticado pelo token de aprovacao dentro do caso de uso.
                    .requestMatchers("/mechanical-hub/service-orders/**")
                    .permitAll()
                    .requestMatchers("/orders", "/orders/**", "/services")
                    .hasAnyRole(MECHANICAL, ADMINISTRATOR)
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    /** O filtro entra so na cadeia do Spring Security, nao como filtro de servlet solto. */
    @Bean
    fun gatewayFilterRegistration(filter: GatewayAuthenticationFilter): FilterRegistrationBean<GatewayAuthenticationFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    /** Sem usuarios locais: a identidade vem do API Gateway (evita o usuario gerado pelo Boot). */
    @Bean
    fun userDetailsService(): UserDetailsService = InMemoryUserDetailsManager()

    private companion object {
        const val MECHANICAL = "MECHANICAL"
        const val ADMINISTRATOR = "ADMINISTRATOR"
    }
}
