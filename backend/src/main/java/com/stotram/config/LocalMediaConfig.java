package com.stotram.config;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves locally stored images and videos straight from the storage directory on disk.
 *
 * <p>Why this exists: {@code LocalImageStorage} writes into {@code app.media.local-dir}
 * ({@code src/main/resources/static} by default), but Spring's built-in static handler
 * serves the <i>build copy</i> of that folder ({@code target/classes/static}), made at
 * startup. An image generated while the server runs was therefore saved fine yet answered
 * 404 until the next rebuild. Reading from the same directory the storage writes to makes a
 * new image viewable the moment it's saved.
 *
 * <p>Goes away with the move to R2/CDN, when media is no longer served by this app.
 */
@Configuration
public class LocalMediaConfig implements WebMvcConfigurer {

    /** Top-level key prefixes: images/<category>/<date>/<uuid>.png, videos/<category>/... */
    private static final String[] FOLDERS = {"images", "videos"};

    private final Path root;

    public LocalMediaConfig(@Value("${app.media.local-dir:src/main/resources/static}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (String folder : FOLDERS) {
            // The trailing slash is required for a directory location.
            String uri = root.resolve(folder).toUri().toString();
            registry.addResourceHandler("/" + folder + "/**")
                    .addResourceLocations(uri.endsWith("/") ? uri : uri + "/")
                    // Keys are unique (UUID) and never overwritten, so browsers may cache freely.
                    .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
        }
    }
}
