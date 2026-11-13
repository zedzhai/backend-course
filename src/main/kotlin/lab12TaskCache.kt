import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

/**
 * Laboratory Work 12 - Step 2: 缓存实现
 *
 * 基于 Caffeine 的进程内缓存：
 *  - expireAfterWrite 10 分钟（写入后过期，避免长期脏数据）
 *  - maximumSize 1000（容量上限，超限按 Window TinyLFU 淘汰）
 *  - recordStats（记录命中率，供 /metrics/cache 与 Prometheus 使用）
 *
 * 缓存 key 为 task id；任务被更新/删除时由 CachedTaskService 主动失效，保证强一致。
 */
class TaskCache(
    expireAfterWriteMinutes: Long = 10,
    maximumSize: Long = 1000
) {
    private val cache: Cache<Int, Task> = Caffeine.newBuilder()
        .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
        .maximumSize(maximumSize)
        .recordStats()
        .build()

    fun get(id: Int): Task? = cache.getIfPresent(id)

    fun put(id: Int, task: Task) {
        cache.put(id, task)
    }

    fun invalidate(id: Int) {
        cache.invalidate(id)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    fun estimatedSize(): Long = cache.estimatedSize()

    /** 与课程要求一致的 Map 形态统计，便于控制台/日志快速查看。 */
    fun getStats(): Map<String, Any> {
        val stats = cache.stats()
        return linkedMapOf(
            "hitCount" to stats.hitCount(),
            "missCount" to stats.missCount(),
            "hitRate" to stats.hitRate(),
            "evictionCount" to stats.evictionCount(),
            "loadCount" to stats.loadCount(),
            "size" to cache.estimatedSize()
        )
    }
}
