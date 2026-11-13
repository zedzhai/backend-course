import kotlinx.serialization.Serializable

// Laboratory Work 8 - Step 2: 数据模型
// 说明：本项目 Lab7 及历史 lab 均位于默认包（无 package 声明），
// 为保持同一模块内可直接互相引用，Lab8 沿用相同约定。

@Serializable
data class UserProfile(
    val userId: Int,
    val name: String,
    val email: String
)

@Serializable
data class Order(
    val orderId: Int,
    val userId: Int,
    val amount: Double
)

@Serializable
data class Recommendation(
    val userId: Int,
    val products: List<String>
)

@Serializable
data class UserDashboard(
    val profile: UserProfile,
    val orders: List<Order>,
    val recommendations: List<Recommendation>,
    val totalSpent: Double
)
