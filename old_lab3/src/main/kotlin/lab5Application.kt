/**
 * Lab5 - Book CRUD Rest API
 */
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class BookCreateRequest(
    val title: String,
    val author: String,
    val year: Int
)

fun main() {
    val bookRepo = lab5Repository()
    val bookService = lab5Service(bookRepo)

    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        routing {
            // 获取全部图书
            get("/api/books") {
                val res = bookService.getAll()
                call.respond(res.list)
            }
            // 根据id查单本
            get("/api/books/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if(id == null) {
                    call.respondText("invalid id")
                    return@get
                }
                val result = bookService.getById(id)
                when(result) {
                    is BookResult.Success -> call.respond(result.book)
                    is BookResult.NotFound -> call.respondText(result.msg)
                    else -> {}
                }
            }
            // 新增图书 POST
            post("/api/books") {
                val req = call.receive<BookCreateRequest>()
                val result = bookService.createBook(req.title, req.author, req.year)
                when(result) {
                    is BookResult.Success -> call.respond(result.book)
                    is BookResult.ValidationError -> call.respond(result.errors)
                    else -> {}
                }
            }
            // 修改图书 PUT
            put("/api/books/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@put
                val req = call.receive<BookCreateRequest>()
                val result = bookService.updateBook(id, req.title, req.author, req.year)
                when(result) {
                    is BookResult.Success -> call.respond(result.book)
                    is BookResult.ValidationError -> call.respond(result.errors)
                    is BookResult.NotFound -> call.respondText(result.msg)
                    else -> {}
                }
            }
            // 删除 DELETE
            delete("/api/books/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@delete
                val result = bookService.deleteBook(id)
                when(result) {
                    is BookResult.Success -> call.respondText("deleted ok")
                    is BookResult.NotFound -> call.respondText(result.msg)
                    else -> {}
                }
            }
        }
    }.start(wait = true)
}
