fun main() {
    val repository = TaskRepository()
    val service = TaskService(repository)

    println("Task Manager")
    println("============")

    while (true) {
        println()
        println("1. List all tasks")
        println("2. Create task")
        println("3. Toggle task completion")
        println("4. Delete task")
        println("5. Search tasks")
        println("6. Show statistics")
        println("0. Exit")
        print("Choose an option: ")

        when (readlnOrNull()) {
            "1" -> listTasks(service)
            "2" -> createTask(service)
            "3" -> toggleTask(service)
            "4" -> deleteTask(service)
            "5" -> searchTasks(service)
            "6" -> showStatistics(service)
            "0" -> {
                println("Goodbye!")
                return
            }
            else -> println("Invalid option")
        }
    }
}

fun listTasks(service: TaskService) {
    val tasks = service.getAllTasks()
    if (tasks.isEmpty()) {
        println("No tasks found.")
        return
    }
    tasks.forEach { task ->
        val status = if (task.completed) "[✓]" else "[ ]"
        println("$status ${task.id}: ${task.title}")
        if (task.description.isNotBlank()) {
            println("     ${task.description}")
        }
        if (task.tags.isNotEmpty()) {
            println("     Tags: ${task.tags.joinToString(", ")}")
        }
    }
}

fun createTask(service: TaskService) {
    print("Title: ")
    val title = readlnOrNull() ?: return
    print("Description (optional): ")
    val description = readlnOrNull() ?: ""
    print("Tags (comma‑separated, optional): ")
    val tagsInput = readlnOrNull() ?: ""

    val tags = if (tagsInput.isBlank()) {
        emptySet()
    } else {
        tagsInput.split(",").map { it.trim() }.toSet()
    }

    when (val result = service.createTask(title, description, tags)) {
        is TaskResult.Success -> println("Task created: ${result.task.id}")
        is TaskResult.ValidationError -> {
            println("Validation errors:")
            result.errors.forEach { println("  - $it") }
        }
        is TaskResult.NotFound -> println(result.message)
    }
}

fun toggleTask(service: TaskService) {
    print("Task ID: ")
    val id = readlnOrNull()?.toIntOrNull() ?: return
    when (val result = service.toggleTask(id)) {
        is TaskResult.Success -> println("Task updated: ${result.task.title}")
        is TaskResult.ValidationError -> println("Unexpected validation error")
        is TaskResult.NotFound -> println(result.message)
    }
}

fun deleteTask(service: TaskService) {
    print("Task ID: ")
    val id = readlnOrNull()?.toIntOrNull() ?: return
    if (service.deleteTask(id)) {
        println("Task deleted.")
    } else {
        println("Task not found.")
    }
}

fun searchTasks(service: TaskService) {
    print("Search query: ")
    val query = readlnOrNull() ?: return
    val tasks = service.getAllTasks().filter {
        it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
    }
    if (tasks.isEmpty()) {
        println("No matching tasks.")
    } else {
        tasks.forEach { println("${it.id}: ${it.title}") }
    }
}

fun showStatistics(service: TaskService) {
    val stats = service.getStatistics()
    println("Statistics:")
    stats.forEach { (key, value) -> println("  $key: $value") }
}

