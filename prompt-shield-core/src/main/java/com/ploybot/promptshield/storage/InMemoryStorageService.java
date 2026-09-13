package com.ploybot.promptshield.storage;

import com.ploybot.promptshield.model.ObfuscationTag;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Heap storage. With a time-to-live, entries expire after that long without being
 * stored or refreshed and are purged periodically, so the map cannot grow without
 * bound and original values do not stay in memory indefinitely.
 */
public class InMemoryStorageService implements StorageService {

    private record Entry(ObfuscationTag tag, long lastUsedNanos) {
    }

    private static final long MAX_PURGE_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, Entry> storage = new ConcurrentHashMap<>();
    /** Zero when entries never expire. */
    private final long ttlNanos;
    private final LongSupplier nanoClock;
    private final AtomicLong lastPurgeNanos;

    /** Storage without expiration. */
    public InMemoryStorageService() {
        this(null);
    }

    /**
     * @param ttl time an entry is kept since it was last stored or refreshed; {@code null},
     *            zero or negative disables expiration
     */
    public InMemoryStorageService(Duration ttl) {
        this(ttl, System::nanoTime);
    }

    InMemoryStorageService(Duration ttl, LongSupplier nanoClock) {
        this.ttlNanos = ttl == null || ttl.isZero() || ttl.isNegative() ? 0 : ttl.toNanos();
        this.nanoClock = nanoClock;
        this.lastPurgeNanos = new AtomicLong(nanoClock.getAsLong());
    }

    @Override
    public void store(String key, ObfuscationTag tag) {
        long now = nanoClock.getAsLong();
        storage.put(key, new Entry(tag, now));
        purgeIfDue(now);
    }

    @Override
    public Optional<ObfuscationTag> storeIfAbsent(String key, ObfuscationTag tag) {
        long now = nanoClock.getAsLong();
        Entry candidate = new Entry(tag, now);
        Entry winner = storage.compute(key, (k, current) ->
                current == null || isExpired(current, now) ? candidate : current);
        purgeIfDue(now);
        return winner == candidate ? Optional.empty() : Optional.of(winner.tag());
    }

    @Override
    public void refresh(String key) {
        if (ttlNanos == 0) {
            return;
        }
        long now = nanoClock.getAsLong();
        storage.computeIfPresent(key, (k, current) ->
                isExpired(current, now) ? null : new Entry(current.tag(), now));
    }

    @Override
    public Optional<ObfuscationTag> retrieve(String key) {
        Entry entry = storage.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, nanoClock.getAsLong())) {
            storage.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.tag());
    }

    @Override
    public boolean contains(String key) {
        return retrieve(key).isPresent();
    }

    @Override
    public void remove(String key) {
        storage.remove(key);
    }

    @Override
    public void clear() {
        storage.clear();
    }

    /** Number of entries, including expired ones not purged yet. */
    public int size() {
        return storage.size();
    }

    /** Removes every expired entry now. */
    public void purgeExpired() {
        long now = nanoClock.getAsLong();
        storage.values().removeIf(entry -> isExpired(entry, now));
    }

    private boolean isExpired(Entry entry, long now) {
        return ttlNanos != 0 && now - entry.lastUsedNanos() >= ttlNanos;
    }

    private void purgeIfDue(long now) {
        if (ttlNanos == 0) {
            return;
        }
        long last = lastPurgeNanos.get();
        if (now - last >= Math.min(ttlNanos, MAX_PURGE_INTERVAL_NANOS)
                && lastPurgeNanos.compareAndSet(last, now)) {
            purgeExpired();
        }
    }
}
