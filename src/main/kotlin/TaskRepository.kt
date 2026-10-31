class TaskRepository {
    private val tasks = mutableListOf<Task>()
    private var nextId = 1

    fun findAll(): List<Task> = tasks.toList()

    fun findById(id: Int): Task? = tasks.find { it.id == id }

    fun findByCompleted(completed: Boolean): List<Task> =
        tasks.filter { it.completed == completed }

    fun findByTag(tag: String): List<Task> =
        tasks.filter { tag in it.tags }

    fun searchByTitle(query: String): List<Task> =
        tasks.filter { it.title.contains(query, ignoreCase = true) }

    fun create(title: String, description: String = "", tags: Set<String> = emptySet()): Task {
        val task = Task(
            id = nextId++,
            title = title,
            description = description,
            tags = tags
        )
        tasks.add(task)
        return task
    }

    fun update(id: Int, title: String? = null, description: String? = null): Task? {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return null
        val current = tasks[index]
        val updated = current.copy(
            title = title ?: current.title,
            description = description ?: current.description
        )
        tasks[index] = updated
        return updated
    }

    fun toggleCompleted(id: Int): Task? {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return null
        val current = tasks[index]
        val updated = current.copy(completed = !current.completed)
        tasks[index] = updated
        return updated
    }

    fun delete(id: Int): Boolean = tasks.removeIf { it.id == id }

    fun count(): Int = tasks.size

    fun getStatistics(): Map<String, Int> = mapOf(
        "total" to tasks.size,
        "completed" to tasks.count { it.completed },
        "pending" to tasks.count { !it.completed }
    )
}
