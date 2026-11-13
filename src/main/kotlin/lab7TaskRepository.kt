interface TaskRepository {
    fun findAll(): List<Task>
    fun findById(id: Int): Task?
    fun create(title: String, description: String, priority: Int): Task
    fun update(id: Int, title: String, description: String, priority: Int): Task?
    fun delete(id: Int): Boolean
    fun toggleCompleted(id: Int): Task?
    fun findByCompleted(completed: Boolean): List<Task>
    fun findByPriorityGreaterThan(priority: Int): List<Task>
}

