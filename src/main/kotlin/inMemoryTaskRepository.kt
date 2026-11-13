import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内存版 TaskRepository。
 * 与 PostgresTaskRepository 行为对齐：id 自增、findAll 按 createdAt 倒序。
 * 供 Lab9 集成测试与 Lab10 认证演示使用，避免测试/演示依赖本机 PostgreSQL。
 */
class InMemoryTaskRepository : TaskRepository {
    private val idGenerator = AtomicInteger(0)
    private val storage = ConcurrentHashMap<Int, Task>()

    override fun findAll(): List<Task> =
        storage.values.sortedByDescending { it.createdAt }

    override fun findById(id: Int): Task? = storage[id]

    override fun create(title: String, description: String, priority: Int): Task {
        val id = idGenerator.incrementAndGet()
        val task = Task(
            id = id,
            title = title,
            description = description,
            completed = false,
            priority = priority,
            createdAt = Instant.now()
        )
        storage[id] = task
        return task
    }

    override fun update(id: Int, title: String, description: String, priority: Int): Task? {
        val current = storage[id] ?: return null
        val updated = current.copy(title = title, description = description, priority = priority)
        storage[id] = updated
        return updated
    }

    override fun delete(id: Int): Boolean = storage.remove(id) != null

    override fun toggleCompleted(id: Int): Task? {
        val current = storage[id] ?: return null
        val updated = current.copy(completed = !current.completed)
        storage[id] = updated
        return updated
    }

    override fun findByCompleted(completed: Boolean): List<Task> =
        storage.values.filter { it.completed == completed }.sortedByDescending { it.createdAt }

    override fun findByPriorityGreaterThan(priority: Int): List<Task> =
        storage.values.filter { it.priority > priority }.sortedByDescending { it.createdAt }
}
