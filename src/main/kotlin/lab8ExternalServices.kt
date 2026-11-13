import kotlinx.coroutines.delay

// Laboratory Work 8 - Step 3: 模拟外部服务（网络延迟）
object ExternalServices {

    /** 模拟远程用户资料服务：500ms */
    suspend fun fetchUserProfile(userId: Int): UserProfile {
        delay(500)
        return UserProfile(userId, "User $userId", "user$userId@example.com")
    }

    /** 模拟远程订单服务：800ms */
    suspend fun fetchOrders(userId: Int): List<Order> {
        delay(800)
        return listOf(
            Order(1, userId, 99.99),
            Order(2, userId, 149.50),
            Order(3, userId, 25.00)
        )
    }

    /** 模拟远程推荐服务：600ms */
    suspend fun fetchRecommendations(userId: Int): List<Recommendation> {
        delay(600)
        return listOf(
            Recommendation(userId, listOf("Product A", "Product B", "Product C"))
        )
    }
}
