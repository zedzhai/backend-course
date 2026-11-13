import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

@Serializable
data class Statistics(
    val total: Int,
    val completed: Int,
    val pending: Int,
    val highPriority: Int,
    val mediumPriority: Int,
    val lowPriority: Int
)

@Serializable
data class Task(
    val id: Int,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val priority: Int = 3,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)

/** java.time.Instant 没有内置 kotlinx 序列化器，统一用 ISO-8601 字符串收发。 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String = "",
    val priority: Int = 3
)

sealed class ServiceResult<out T> {
    data class Success<T>(val data: T) : ServiceResult<T>()
    data class ValidationFailure(val errors: List<String>) : ServiceResult<Nothing>()
    data class NotFound(val message: String) : ServiceResult<Nothing>()
}

