package com.automatedinterview.ai;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "app.kafka.indexing.enabled", havingValue = "true")
public class KafkaIndexingConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka,
                                          KafkaIndexingProperties properties,
                                          QuestionIndexingStateRepository state,
                                          ObjectMapper json) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
            kafka, (record, exception) -> new TopicPartition(properties.dltTopic(), record.partition()));
        return new DefaultErrorHandler((record, exception) -> {
            try {
                QuestionIndexingEvent event = json.readValue(String.valueOf(record.value()), QuestionIndexingEvent.class);
                state.markFailed(event.questionId(), exception.getMessage() == null ? "Kafka indexing failed" : exception.getMessage());
            } catch (Exception ignored) {
                // The original malformed record is still preserved in the DLT.
            }
            dlt.accept(record, exception);
        }, new FixedBackOff(5_000L, 2L));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler errorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
