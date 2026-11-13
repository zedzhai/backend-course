import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryTaskRepository : TaskRepository {

    private val tasks = ConcurrentHashMap<Int, Task>()
    private val nextId = AtomicInteger(1)

    override fun findAll(): List<Task> {
        return tasks.values.sortedBy { it.id }
    }

    override fun findById(id: Int): Task? {
        return tasks[id]
    }

    override fun create(task: Task): Task {
        val newId = nextId.getAndIncrement()
        val newTask = task.copy(id = newId)
        tasks[newId] = newTask
        return newTask
    }

    override fun create(title: String, description: String, tags: Set<String>): Task {
        val newId = nextId.getAndIncrement()
        val newTask = Task(
            id = newId,
            title = title,
            description = description,
            completed = false,
            tags = tags
        )
        tasks[newId] = newTask
        return newTask
    }

    override fun update(id: Int, task: Task): Task? {
        val existing = tasks[id] ?: return null
        val updated = existing.copy(
            title = task.title,
            description = task.description,
            tags = task.tags
        )
        tasks[id] = updated
        return updated
    }

    override fun delete(id: Int): Boolean {
        return tasks.remove(id) != null
    }

    override fun toggleCompleted(id: Int): Task? {
        val existing = tasks[id] ?: return null
        val updated = existing.copy(completed = !existing.completed)
        tasks[id] = updated
        return updated
    }

    override fun getStatistics(): Map<String, Int> {
        val all = tasks.values.toList()
        val total = all.size
        val completed = all.count { it.completed }
        return mapOf(
            "total" to total,
            "completed" to completed,
            "pending" to (total - completed)
        )
    }
}

