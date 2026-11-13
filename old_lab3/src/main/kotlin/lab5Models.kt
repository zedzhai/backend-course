/**
 * Lab5 - Book Model & Result sealed class
 */

data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val year: Int
)

sealed class BookResult {
    data class Success(val book: Book) : BookResult()
    data class SuccessList(val list: List<Book>) : BookResult()
    data class NotFound(val msg: String) : BookResult()
    data class ValidationError(val errors: List<String>) : BookResult()
}
