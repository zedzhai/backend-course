import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResp(val error: String)
@Serializable
data class ValidateResp(val errors: List<String>)

fun Route.taskRoutes(service: TaskService) {
    route("/api/tasks") {
        get {
            when(val res = service.getAllTasks()){
                is ServiceResult.Success -> call.respond(res.data)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id==null) return@get call.respond(HttpStatusCode.BadRequest,ErrorResp("bad id"))
            when(val res = service.getTask(id)){
                is ServiceResult.Success -> call.respond(res.data)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }

        post {
            val req = call.receive<CreateTaskRequest>()
            when(val res = service.createTask(req)){
                is ServiceResult.Success -> call.respond(HttpStatusCode.Created,res.data)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest,ErrorResp("bad id"))
            val req = call.receive<CreateTaskRequest>()
            when(val res = service.updateTask(id,req)){
                is ServiceResult.Success -> call.respond(res.data)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }

        patch("/{id}/toggle") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest,ErrorResp("bad id"))
            when(val res = service.toggle(id)){
                is ServiceResult.Success -> call.respond(res.data)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest,ErrorResp("bad id"))
            when(val res = service.deleteTask(id)){
                is ServiceResult.Success -> call.respond(HttpStatusCode.NoContent)
                is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
            }
        }
    }
}
