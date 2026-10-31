class TaskService(private val repository: TaskRepository) {

    fun createTask(title: String, description: String = "", tags: Set<String> = emptySet()): TaskResult {
        val errors = mutableListOf<String>()
        if (title.isBlank()) {
            errors.add("Title cannot be blank")
        }
        if (title.length > 100) {
            errors.add("Title cannot exceed 100 characters")
        }
        if (description.length > 1000) {
            errors.add("Description cannot exceed 1000 characters")
        }
        if (errors.isNotEmpty()) {
            return TaskResult.ValidationError(errors)
        }
        val task = repository.create(title, description, tags)
        return TaskResult.Success(task)
    }

    fun getTask(id: Int): TaskResult {
        val task = repository.findById(id)
        return if (task != null) {
            TaskResult.Success(task)
        } else {
            TaskResult.NotFound("Task with id $id not found")
        }
    }

    fun getAllTasks(): List<Task> = repository.findAll()

    fun toggleTask(id: Int): TaskResult {
        val task = repository.toggleCompleted(id)
        return if (task != null) {
            TaskResult.Success(task)
        } else {
            TaskResult.NotFound("Task with id $id not found")
        }
    }

    fun deleteTask(id: Int): Boolean = repository.delete(id)

    fun getStatistics(): Map<String, Int> = repository.getStatistics()
}
