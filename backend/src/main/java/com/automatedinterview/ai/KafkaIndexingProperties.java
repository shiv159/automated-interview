package com.automatedinterview.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.indexing")
public record KafkaIndexingProperties(
    boolean enabled,
    String topic,
    String dltTopic,
    String groupId,
    long recoveryMs
) {
}
