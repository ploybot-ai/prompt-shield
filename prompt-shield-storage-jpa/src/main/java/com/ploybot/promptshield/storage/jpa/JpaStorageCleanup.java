package com.ploybot.promptshield.storage.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Deletes expired mappings periodically on its own daemon thread, so it works without
 * the application enabling Spring scheduling.
 */
public class JpaStorageCleanup implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(JpaStorageCleanup.class);

    private final JpaStorageService storageService;
    private final long intervalMinutes;
    private ScheduledExecutorService executor;

    public JpaStorageCleanup(JpaStorageService storageService, long intervalMinutes) {
        if (intervalMinutes <= 0) {
            throw new IllegalArgumentException("prompt-shield.storage.jpa.cleanup-interval-minutes must be greater than zero");
        }
        this.storageService = storageService;
        this.intervalMinutes = intervalMinutes;
    }

    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "prompt-shield-jpa-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runCleanup, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }

    void runCleanup() {
        try {
            int deleted = storageService.cleanupExpired();
            if (deleted > 0) {
                logger.debug("Deleted {} expired obfuscation mappings", deleted);
            }
        } catch (RuntimeException e) {
            logger.warn("Could not delete expired obfuscation mappings: {}", e.getMessage());
        }
    }
}
