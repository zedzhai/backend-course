/**
 * Laboratory Work 12 - Step 3: 带缓存的服务
 *
 * 装饰器实现：继承 TaskService 复用「标题非空 / 长度 <= 100」等校验与 NotFound 语义，
 * 只在读路径加缓存、写路径做失效（Cache-Aside 模式）：
 *  - getTask：先查缓存，命中直接返回；未命中回源并在成功时回填
 *  - createTask / updateTask / toggle：写库成功后同步回填缓存
 *  - deleteTask：删除成功后失效缓存
 *  - getAllTasks：列表不走缓存（避免集合级失效成本与脏读）
 *
 * 由于它是 TaskService 的子类型，现有 taskRoutes(service: TaskService) 可直接复用，
 * 无需为缓存版本复制一套路由。
 */
class CachedTaskService(
    repository: TaskRepository,
    private val cache: TaskCache
) : TaskService(repository) {

    override fun getTask(id: Int): ServiceResult<Task> {
        // 1) 缓存命中
        cache.get(id)?.let { return ServiceResult.Success(it) }

        // 2) 缓存未命中：回源，成功则回填
        val result = super.getTask(id)
        if (result is ServiceResult.Success) {
            cache.put(id, result.data)
        }
        return result
    }

    override fun createTask(req: CreateTaskRequest): ServiceResult<Task> {
        val result = super.createTask(req)
        if (result is ServiceResult.Success) {
            cache.put(result.data.id, result.data)
        }
        return result
    }

    override fun updateTask(id: Int, req: CreateTaskRequest): ServiceResult<Task> {
        val result = super.updateTask(id, req)
        return when (result) {
            is ServiceResult.Success -> {
                cache.put(id, result.data)
                result
            }
            is ServiceResult.NotFound -> {
                // 数据已不存在，缓存里的旧值必须清掉
                cache.invalidate(id)
                result
            }
            else -> result
        }
    }

    override fun toggle(id: Int): ServiceResult<Task> {
        val result = super.toggle(id)
        return when (result) {
            is ServiceResult.Success -> {
                cache.put(id, result.data)
                result
            }
            is ServiceResult.NotFound -> {
                cache.invalidate(id)
                result
            }
            else -> result
        }
    }

    override fun deleteTask(id: Int): ServiceResult<Unit> {
        val result = super.deleteTask(id)
        if (result is ServiceResult.Success) {
            cache.invalidate(id)
        }
        return result
    }

    /** 供 /metrics/cache 与 Prometheus 使用。 */
    fun getCacheStats(): Map<String, Any> = cache.getStats()

    fun cacheInstance(): TaskCache = cache
}
