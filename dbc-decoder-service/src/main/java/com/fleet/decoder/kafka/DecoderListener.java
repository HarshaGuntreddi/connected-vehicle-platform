package com.fleet.decoder.kafka;

import com.fleet.common.dto.CanFrame;
import com.fleet.common.dto.DecodedTelemetry;
import com.fleet.decoder.dbc.CanDecoder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes raw CAN frames, decodes them via the DBC-driven {@link CanDecoder},
 * and republishes human-readable telemetry. Frames with unknown message IDs are
 * counted and dropped.
 */
@Component
public class DecoderListener {

    private static final Logger log = LoggerFactory.getLogger(DecoderListener.class);

    private final CanDecoder decoder;
    private final KafkaTemplate<String, DecodedTelemetry> kafkaTemplate;
    private final String outTopic;
    private final Counter decodedCounter;
    private final Counter unknownCounter;

    public DecoderListener(CanDecoder decoder,
                           KafkaTemplate<String, DecodedTelemetry> kafkaTemplate,
                           @Value("${topic.decoded:decoded-telemetry}") String outTopic,
                           MeterRegistry registry) {
        this.decoder = decoder;
        this.kafkaTemplate = kafkaTemplate;
        this.outTopic = outTopic;
        this.decodedCounter = Counter.builder("telemetry.decoded")
                .description("Total telemetry messages decoded and published").register(registry);
        this.unknownCounter = Counter.builder("can.frames.unknown")
                .description("CAN frames with a message ID not present in the DBC").register(registry);
    }

    @KafkaListener(topics = "${topic.raw-can:raw-can-frames}", groupId = "dbc-decoder")
    public void onFrame(CanFrame frame) {
        decoder.decode(frame).ifPresentOrElse(
                telemetry -> {
                    kafkaTemplate.send(outTopic, telemetry.vehicleId(), telemetry);
                    decodedCounter.increment();
                },
                unknownCounter::increment);
    }
}
