import org.mindrot.jbcrypt.BCrypt
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// Laboratory Work 10 - Step 2: 用户模型与仓库
data class User(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val role: String = "USER"
)

object UserRepository {
    private val idGenerator = AtomicInteger(0)

    private val users = CopyOnWriteArrayList(
        listOf(
            User(idGenerator.incrementAndGet(), "admin", BCrypt.hashpw("admin123", BCrypt.gensalt()), "ADMIN"),
            User(idGenerator.incrementAndGet(), "alice", BCrypt.hashpw("alice123", BCrypt.gensalt()), "USER"),
            User(idGenerator.incrementAndGet(), "bob", BCrypt.hashpw("bob123", BCrypt.gensalt()), "USER")
        )
    )

    fun findByUsername(username: String): User? =
        users.find { it.username == username }

    fun findById(id: Int): User? = users.find { it.id == id }

    fun validatePassword(user: User, password: String): Boolean =
        BCrypt.checkpw(password, user.passwordHash)

    /** 注册普通用户；用户名已存在时返回 null */
    fun register(username: String, password: String): User? {
        if (username.isBlank() || password.length < 6) return null
        if (findByUsername(username) != null) return null
        val user = User(
            id = idGenerator.incrementAndGet(),
            username = username,
            passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
            role = "USER"
        )
        users.add(user)
        return user
    }
}
