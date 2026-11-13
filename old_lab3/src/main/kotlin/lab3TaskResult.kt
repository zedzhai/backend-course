sealed class TaskResult {
    data class Success(val task: Task) : TaskResult()
    data class ValidationError(val messages: List<String>) : TaskResult()
    data class NotFound(val message: String) : TaskResult()
}

