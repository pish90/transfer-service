package com.fintech.transfer_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        // Add event listeners for all circuit breakers
        registry.getEventPublisher().onEntryAdded(event -> {
            CircuitBreaker circuitBreaker = event.getAddedEntry();
            addCircuitBreakerListeners(circuitBreaker);
        });

        return registry;
    }

    private void addCircuitBreakerListeners(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Circuit breaker {} state transition: {} -> {} at {}",
                                circuitBreaker.getName(),
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState(),
                                event.getCreationTime())
                );

        circuitBreaker.getEventPublisher()
                .onFailureRateExceeded(event ->
                        log.warn("Circuit breaker {} failure rate exceeded: {}%",
                                circuitBreaker.getName(), event.getFailureRate())
                );

        circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event ->
                        log.warn("Circuit breaker {} call not permitted",
                                circuitBreaker.getName())
                );
    }
}
