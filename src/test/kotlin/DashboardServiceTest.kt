import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Laboratory Work 8 - 并发聚合验证：结果正确 + 耗时接近最长单次延迟（800ms）
class DashboardServiceTest {

    private val service = DashboardService()

    @Test
    fun `getUserDashboard should aggregate data and compute totalSpent`() = runBlocking {
        val dashboard = service.getUserDashboard(1)

        assertEquals(1, dashboard.profile.userId)
        assertEquals("User 1", dashboard.profile.name)
        assertEquals("user1@example.com", dashboard.profile.email)
        assertEquals(3, dashboard.orders.size)
        assertEquals(274.49, dashboard.totalSpent, 0.001)
        assertEquals(1, dashboard.recommendations.size)
        assertEquals(listOf("Product A", "Product B", "Product C"), dashboard.recommendations[0].products)
    }

    @Test
    fun `getUserDashboard should run external calls concurrently`() = runBlocking {
        val elapsed = measureTimeMillis { service.getUserDashboard(2) }

        // 串行需 1900ms；并发应接近 800ms（最长延迟）
        assertTrue(elapsed < 1500, "耗时 $elapsed ms 过久，疑似未并发执行")
        assertTrue(elapsed >= 700, "耗时 $elapsed ms 异常偏小，延迟可能未生效")
    }

    @Test
    fun `getMultipleDashboards should fetch 5 users in about one round`() = runBlocking {
        val elapsed = measureTimeMillis {
            val dashboards = service.getMultipleDashboards((1..5).toList())
            assertEquals(5, dashboards.size)
            assertEquals((1..5).toList(), dashboards.map { it.profile.userId })
        }

        // 串行需 9500ms；并发应接近 800ms
        assertTrue(elapsed < 2500, "批量耗时 $elapsed ms 过久，疑似串行执行")
    }
}
