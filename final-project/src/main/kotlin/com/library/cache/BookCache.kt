package com.library.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.library.domain.Book
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

/** Serialisable snapshot of the Caffeine statistics, exposed on /metrics/cache. */
@Serializable
data class CacheStatsSnapshot(
    val provider: String = "Caffeine",
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictionCount: Long,
    val size: Long
)

/**
 * Caffeine-backed cache for the "get book by id" endpoint.
 *
 * TTL 5 minutes, max 1000 entries, statistics enabled so the hit ratio can be
 * scraped by Prometheus and inspected through /metrics/cache.
 */
class BookCache(
    maxSize: Long = 1000,
    ttlMinutes: Long = 5
) {
    private val cache: Cache<Int, Book> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build()

    fun get(id: Int): Book? = cache.getIfPresent(id)

    fun put(book: Book) {
        cache.put(book.id, book)
    }

    fun invalidate(id: Int) {
        cache.invalidate(id)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    fun size(): Long = cache.estimatedSize()

    fun stats(): CacheStatsSnapshot {
        val snapshot = cache.stats()
        val requests = snapshot.hitCount() + snapshot.missCount()
        val hitRate = if (requests == 0L) 0.0 else snapshot.hitCount().toDouble() / requests
        return CacheStatsSnapshot(
            hitCount = snapshot.hitCount(),
            missCount = snapshot.missCount(),
            hitRate = hitRate,
            evictionCount = snapshot.evictionCount(),
            size = size()
        )
    }
}
