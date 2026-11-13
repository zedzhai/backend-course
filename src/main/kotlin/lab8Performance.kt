import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

// Laboratory Work 8 - Step 5: 性能测量
// 预期：单用户约 800ms（三个延迟中的最大值），而非 1900ms（三者之和）
fun main() = runBlocking {
    val service = DashboardService()

    println("Fetching dashboard for user 1...")
    val time = measureTimeMillis {
        val dashboard: UserDashboard = service.getUserDashboard(1)
        println("Profile: ${dashboard.profile.name}")
        println("Orders: ${dashboard.orders.size}")
        println("Recommendations: ${dashboard.recommendations.size}")
        println("Total spent: ${dashboard.totalSpent}")
    }
    println("Time taken: ${time}ms")
    println("Sequential expectation would be: 1900ms")

    println()
    println("Fetching dashboards for users 1-5 concurrently...")
    val batchTime = measureTimeMillis {
        val dashboards = service.getMultipleDashboards((1..5).toList())
        println("Fetched ${dashboards.size} dashboards")
    }
    println("Time taken: ${batchTime}ms")
    println("Sequential expectation would be: 9500ms")
}
