import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Laboratory Work 8 - Step 4: 并发聚合三个外部服务
class DashboardService {

    suspend fun getUserDashboard(userId: Int): UserDashboard = coroutineScope {
        val profileDeferred = async { ExternalServices.fetchUserProfile(userId) }
        val ordersDeferred = async { ExternalServices.fetchOrders(userId) }
        val recommendationsDeferred = async { ExternalServices.fetchRecommendations(userId) }

        val profile = profileDeferred.await()
        val orders = ordersDeferred.await()
        val recommendations = recommendationsDeferred.await()

        val totalSpent = orders.sumOf { it.amount }

        UserDashboard(
            profile = profile,
            orders = orders,
            recommendations = recommendations,
            totalSpent = totalSpent
        )
    }

    /** 多用户批量并发，总耗时约等于单用户耗时而不随用户数线性增长 */
    suspend fun getMultipleDashboards(userIds: List<Int>): List<UserDashboard> =
        coroutineScope {
            userIds.map { userId ->
                async { getUserDashboard(userId) }
            }.map { it.await() }
        }

    /** 串行版本，仅用于性能对比 */
    suspend fun getMultipleDashboardsSequential(userIds: List<Int>): List<UserDashboard> =
        userIds.map { getUserDashboard(it) }
}
