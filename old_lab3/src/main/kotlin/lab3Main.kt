fun main() {
    val repo = InMemoryTaskRepository()
    val service = lab3TaskService(repo)

    println("===== Task Manager =====")
    while (true) {
        println()
        println("1. List all tasks")
        println("2. Create task")
        println("3. Toggle task completion")
        println("4. Delete task")
        println("5. Show statistics")
        println("0. Exit")
        print("Choose option: ")

        val option = readlnOrNull()?.toIntOrNull()
        when (option) {
            1 -> {
                val tasks = service.getAllTasks()
                if (tasks.isEmpty()) {
                    println("No tasks.")
                } else {
                    tasks.forEach {
                        println("[${if (it.completed) "✓" else " "}] id:${it.id} | ${it.title} | ${it.description} | tags:${it.tags}")
                    }
                }
            }
            2 -> {
                print("Enter title: ")
                val title = readln()
                print("Enter description: ")
                val desc = readln()
                print("Enter tags (split by comma): ")
                val tagInput = readln()
                val tags = tagInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

                val result = service.createTask(title, desc, tags)
                when (result) {
                    is TaskResult.Success -> println("Created task: ${result.task}")
                    is TaskResult.ValidationError -> println("Error: ${result.messages.joinToString()}")
                    is TaskResult.NotFound -> println("Not found")
                }
            }
            3 -> {
                print("Enter task id to toggle: ")
                val id = readlnOrNull()?.toIntOrNull()
                if (id == null) {
                    println("Invalid id")
                    continue
                }
                val res = service.toggleTask(id)
                when (res) {
                    is TaskResult.Success -> println("Updated: ${res.task}")
                    is TaskResult.NotFound -> println("Task not found")
                    else -> {}
                }
            }
            4 -> {
                print("Enter task id to delete: ")
                val id = readlnOrNull()?.toIntOrNull()
                if (id == null) {
                    println("Invalid id")
                    continue
                }
                val ok = service.deleteTask(id)
                println(if (ok) "Deleted" else "Not found")
            }
            5 -> {
                val stat = service.getStatistics()
                println("Stats: $stat")
            }
            0 -> {
                println("Bye")
                return
            }
            else -> println("Invalid option")
        }
    }
}


