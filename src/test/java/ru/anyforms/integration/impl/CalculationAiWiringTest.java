package ru.anyforms.integration.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import ru.anyforms.integration.CalculationAiGateway;
import ru.anyforms.service.calculator.CalculatorAiService;
import ru.anyforms.service.calculator.CalculatorReferenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CalculationAiWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfig.class);

    @Test
    void withoutProviderThereIsNoGatewayAndAiIsHidden() {
        runner.withPropertyValues("calculator.ai.provider=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CalculationAiGateway.class);
            assertThat(context.getBean(CalculatorAiService.class).available()).isFalse();
        });
    }

    @Test
    void openAiProviderCreatesGatewayWithDefaults() {
        runner.withPropertyValues("calculator.ai.provider=openai", "calculator.ai.openai.api-key=sk-test").run(context -> {
            assertThat(context).hasSingleBean(CalculationAiGateway.class);
            CalculatorAiService ai = context.getBean(CalculatorAiService.class);
            assertThat(ai.available()).isTrue();
            assertThat(ai.providerName()).isEqualTo("openai:gpt-6.1-sol");
        });
    }

    @Test
    void modelIsConfigurable() {
        runner.withPropertyValues("calculator.ai.provider=openai", "calculator.ai.openai.api-key=sk-test",
                        "calculator.ai.openai.model=gpt-6-astra")
                .run(context -> assertThat(context.getBean(CalculatorAiService.class).providerName())
                        .isEqualTo("openai:gpt-6-astra"));
    }

    @Test
    void openAiWithoutKeyFailsOnStartup() {
        runner.withPropertyValues("calculator.ai.provider=openai").run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @ComponentScan(basePackageClasses = CalculationAiGateway.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CalculationAiGateway.class))
    @ComponentScan(basePackageClasses = CalculatorAiService.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CalculatorAiService.class))
    static class AiConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CalculatorReferenceService calculatorReferenceService() {
            return mock(CalculatorReferenceService.class);
        }
    }
}
