package com.stotram.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * The pool image generation runs on. Three properties of it are load-bearing:
     *
     * <ul>
     *   <li><b>Separate from Tomcat's pool.</b> A generation run takes minutes; doing it on a
     *       request thread would hold that thread hostage and time the caller out. Keeping it
     *       here also means a wedged image API can never starve the public feed.
     *   <li><b>Bounded, not unlimited.</b> Every provider rate-limits and every image costs
     *       money, so unbounded fan-out buys 429s and a surprise bill. Four concurrent calls is
     *       a deliberate ceiling, not an accident of CPU count.
     *   <li><b>Not the common ForkJoinPool.</b> {@code CompletableFuture.supplyAsync(task)}
     *       without an explicit executor silently uses the JVM-wide common pool, which is
     *       sized to CPU count and shared with everything else. Always pass this bean.
     * </ul>
     *
     * <p>{@code CallerRunsPolicy} on saturation means an over-full queue slows the submitter
     * down rather than throwing work away.
     */
    @Bean("imageExecutor")
    public Executor imageExecutor(
            @Value("${app.generation.concurrency:4}") int concurrency,
            @Value("${app.generation.queue-capacity:200}") int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("img-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
