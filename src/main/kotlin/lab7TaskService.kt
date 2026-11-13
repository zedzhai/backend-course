/**
 * 任务服务：校验 + 业务编排。
 * Lab12 的 CachedTaskService 以装饰器方式继承本类，复用同一套校验逻辑与路由（行为未变）。
 */
open class TaskService(private val repository: TaskRepository) {
    open fun getAllTasks(): ServiceResult<List<Task>> {
        return ServiceResult.Success(repository.findAll())
    }

    open fun getTask(id: Int): ServiceResult<Task> {
        val t = repository.findById(id)
        return if(t!=null) ServiceResult.Success(t)
        else ServiceResult.NotFound("Task $id not found")
    }

    open fun createTask(req: CreateTaskRequest): ServiceResult<Task> {
        val err = mutableListOf<String>()
        if(req.title.isBlank()) err.add("Title cannot be blank")
        if(req.title.length>100) err.add("Title max 100 chars")
        if(err.isNotEmpty()) return ServiceResult.ValidationFailure(err)
        val task = repository.create(req.title,req.description,req.priority)
        return ServiceResult.Success(task)
    }

    open fun updateTask(id: Int, req: CreateTaskRequest): ServiceResult<Task> {
        val updated = repository.update(id,req.title,req.description,req.priority)
        return if(updated!=null) ServiceResult.Success(updated)
        else ServiceResult.NotFound("Task $id not found")
    }

    open fun toggle(id: Int): ServiceResult<Task> {
        val t = repository.toggleCompleted(id)
        return if(t!=null) ServiceResult.Success(t)
        else ServiceResult.NotFound("Task $id not found")
    }

    open fun deleteTask(id: Int): ServiceResult<Unit> {
        val ok = repository.delete(id)
        return if(ok) ServiceResult.Success(Unit)
        else ServiceResult.NotFound("Task $id not found")
    }
}

