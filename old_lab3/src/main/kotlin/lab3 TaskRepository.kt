interface TaskRepository {
    fun findAll(): List<Task>
    fun findById(id: Int): Task?
    fun create(task: Task): Task
    fun create(title: String, description: String, tags: Set<String>): Task
    fun update(id: Int, task: Task): Task?
    fun delete(id: Int): Boolean
    fun toggleCompleted(id: Int): Task?
    fun getStatistics(): Map<String, Int>
}