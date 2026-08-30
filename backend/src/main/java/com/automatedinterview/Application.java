package com.automatedinterview;

import com.automatedinterview.ai.AiConfigurationValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.automatedinterview.ai.AiProperties;
import com.automatedinterview.config.QuestionLimitsProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, QuestionLimitsProperties.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Application.class);
        application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> AiConfigurationValidator.validate(event.getEnvironment()));
        application.run(args);
    }
}
