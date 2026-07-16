package com.fleet.ingestion.kafka;

import com.fleet.common.dto.CanFrame;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes raw {@link CanFrame}s to the configured Kafka topic and tracks a
 * Micrometer counter ({@code can_frames_published_total}) for observability.
 * The vehicleId is used as the message key so all frames from one vehicle land
 * on the same partition (preserving per-vehicle ordering).
 */
@Component
public class CanFrameProducer {

    private static final Logger log = LoggerFactory.getLogger(CanFrameProducer.class);

    private final KafkaTemplate<String, CanFrame> kafkaTemplate;
    private final String topic;
    private final Counter publishedCounter;

    public CanFrameProducer(KafkaTemplate<String, CanFrame> kafkaTemplate,
                            @Value("${topic.raw-can:raw-can-frames}") String topic,
                            MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishedCounter = Counter.builder("can.frames.published")
                .description("Total raw CAN frames published to Kafka")
                .register(registry);
    }

    public void publish(CanFrame frame) {
        kafkaTemplate.send(topic, frame.vehicleId(), frame)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CAN frame for {}: {}", frame.vehicleId(), ex.getMessage());
                    } else {
                        publishedCounter.increment();
                    }
                });
    }
}
