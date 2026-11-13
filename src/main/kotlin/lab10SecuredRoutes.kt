import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

// Laboratory Work 10 - Step 5: 受保护路由 + 角色校验
fun Route.securedTaskRoutes(taskService: TaskService) {
    route("/api/tasks") {
        authenticate("auth-jwt") {
            get {
                // 演示项目：管理员与普通用户均返回全量任务；生产环境应按 token 中的 userId 过滤
                when (val res = taskService.getAllTasks()) {
                    is ServiceResult.Success -> call.respond(res.data)
                    is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                    is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
                }
            }

            post {
                val request = call.receive<CreateTaskRequest>()
                when (val res = taskService.createTask(request)) {
                    is ServiceResult.Success -> call.respond(HttpStatusCode.Created, res.data)
                    is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(res.errors))
                    is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResp(res.message))
                }
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()

                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    return@delete
                }

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@delete
                }

                when (val result = taskService.deleteTask(id)) {
                    is ServiceResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is ServiceResult.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                    is ServiceResult.ValidationFailure -> call.respond(HttpStatusCode.BadRequest, ValidateResp(result.errors))
                }
            }
        }
    }
}
