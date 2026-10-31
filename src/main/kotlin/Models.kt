import java.time.LocalDateTime

data class Task(
    val id: Int,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val tags: Set<String> = emptySet()
)

enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

sealed class TaskResult {
    data class Success(val task: Task) : TaskResult()
    data class ValidationError(val errors: List<String>) : TaskResult()
    data class NotFound(val message: String) : TaskResult()
}
