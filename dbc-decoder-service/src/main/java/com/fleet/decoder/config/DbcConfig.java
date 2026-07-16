package com.fleet.decoder.config;

import com.fleet.decoder.dbc.CanDecoder;
import com.fleet.decoder.dbc.DbcFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

/**
 * Loads the DBC file (from the {@code dbc.file} location, default the bundled
 * {@code classpath:sample.dbc}) and exposes a ready-to-use {@link CanDecoder}.
 */
@Configuration
public class DbcConfig {

    private static final Logger log = LoggerFactory.getLogger(DbcConfig.class);

    @Bean
    public DbcFile dbcFile(ResourceLoader resourceLoader,
                           @Value("${dbc.file:classpath:sample.dbc}") String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            DbcFile file = DbcFile.parse(in);
            log.info("Loaded DBC '{}' with {} message definition(s)", location, file.messageCount());
            return file;
        }
    }

    @Bean
    public CanDecoder canDecoder(DbcFile dbcFile) {
        return new CanDecoder(dbcFile);
    }
}
