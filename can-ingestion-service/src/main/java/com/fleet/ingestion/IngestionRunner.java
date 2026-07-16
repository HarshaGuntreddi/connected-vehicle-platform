package com.fleet.ingestion;

import com.fleet.common.dto.CanFrame;
import com.fleet.ingestion.kafka.CanFrameProducer;
import com.fleet.ingestion.source.CanSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * On startup, selects the active {@link CanSource} (simulator or hardware,
 * decided by the {@code can.mode} property via {@code @ConditionalOnProperty})
 * and streams every frame it produces into Kafka through {@link CanFrameProducer}.
 */
@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final CanSource source;
    private final CanFrameProducer producer;
    private final AtomicLong framesSeen = new AtomicLong();
    private final AtomicReference<Instant> lastFrameAt = new AtomicReference<>();

    public IngestionRunner(CanSource source, CanFrameProducer producer) {
        this.source = source;
        this.producer = producer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting CAN ingestion using source: {}", source.name());
        source.start(frame -> {
            framesSeen.incrementAndGet();
            lastFrameAt.set(frame.timestamp());
            producer.publish(frame);
        });
    }

    public String sourceName() {
        return source.name();
    }

    public long framesSeen() {
        return framesSeen.get();
    }

    public Instant lastFrameAt() {
        return lastFrameAt.get();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Stopping CAN ingestion source");
        source.stop();
    }
}
