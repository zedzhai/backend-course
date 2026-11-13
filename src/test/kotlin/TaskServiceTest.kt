import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// Laboratory Work 9 - Step 2: 服务层单元测试（MockK 隔离仓库）
class TaskServiceTest {

    private lateinit var repository: TaskRepository
    private lateinit var service: TaskService

    @BeforeTest
    fun setup() {
        repository = mockk()
        service = TaskService(repository)
    }

    /** 断言结果为 Success 并取出数据，避免在测试里做裸泛型强制转换 */
    @Suppress("UNCHECKED_CAST")
    private fun <T> assertSuccess(result: ServiceResult<T>): T {
        assertTrue(result is ServiceResult.Success<*>, "期望 Success，实际为 $result")
        return (result as ServiceResult.Success<T>).data
    }

    private fun assertValidationFailure(result: ServiceResult<*>): List<String> =
        when (result) {
            is ServiceResult.ValidationFailure -> result.errors
            else -> fail("期望 ValidationFailure，实际为 $result")
        }

    private fun assertNotFound(result: ServiceResult<*>) {
        if (result !is ServiceResult.NotFound) fail("期望 NotFound，实际为 $result")
    }

    @Test
    fun `createTask should return success when input is valid`() {
        val request = CreateTaskRequest("Learn Testing", "Write tests", 3)
        val expectedTask = Task(1, "Learn Testing", "Write tests", priority = 3)

        every { repository.create("Learn Testing", "Write tests", 3) } returns expectedTask

        val data = assertSuccess(service.createTask(request))

        assertEquals(expectedTask, data)
        verify(exactly = 1) { repository.create("Learn Testing", "Write tests", 3) }
    }

    @Test
    fun `createTask should return validation error when title is blank`() {
        val request = CreateTaskRequest("", "Description", 3)

        val errors = assertValidationFailure(service.createTask(request))

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Title"))
        verify(exactly = 0) { repository.create(any(), any(), any()) }
    }

    @Test
    fun `createTask should return validation error when title is too long`() {
        val request = CreateTaskRequest("T".repeat(101), "Description", 3)

        val errors = assertValidationFailure(service.createTask(request))

        assertTrue(errors.any { it.contains("100") })
        verify(exactly = 0) { repository.create(any(), any(), any()) }
    }

    @Test
    fun `getTask should return success when task exists`() {
        val task = Task(1, "Existing Task", "Description")
        every { repository.findById(1) } returns task

        assertEquals(task, assertSuccess(service.getTask(1)))
    }

    @Test
    fun `getTask should return not found when task does not exist`() {
        every { repository.findById(999) } returns null

        assertNotFound(service.getTask(999))
    }

    @Test
    fun `deleteTask should return not found when task does not exist`() {
        every { repository.delete(999) } returns false

        assertNotFound(service.deleteTask(999))
    }

    @Test
    fun `deleteTask should return success when task is deleted`() {
        every { repository.delete(1) } returns true

        assertSuccess(service.deleteTask(1))
        verify(exactly = 1) { repository.delete(1) }
    }

    @Test
    fun `updateTask should return success with updated data`() {
        val updated = Task(1, "Updated", "Updated desc", priority = 5)
        every { repository.update(1, "Updated", "Updated desc", 5) } returns updated

        assertEquals(updated, assertSuccess(service.updateTask(1, CreateTaskRequest("Updated", "Updated desc", 5))))
    }

    @Test
    fun `updateTask should return not found when task does not exist`() {
        every { repository.update(999, "x", "y", 3) } returns null

        assertNotFound(service.updateTask(999, CreateTaskRequest("x", "y", 3)))
    }

    @Test
    fun `toggle should return success when task exists`() {
        val toggled = Task(1, "Task", "desc", completed = true)
        every { repository.toggleCompleted(1) } returns toggled

        assertEquals(toggled, assertSuccess(service.toggle(1)))
    }

    @Test
    fun `getAllTasks should delegate to repository`() {
        val tasks = listOf(Task(1, "A"), Task(2, "B"))
        every { repository.findAll() } returns tasks

        assertEquals(tasks, assertSuccess(service.getAllTasks()))
        verify(exactly = 1) { repository.findAll() }
    }
}
