/**
 * Lab4 - Ktor HelloWorld REST API
 */
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        routing {
            get("/") {
                call.respondText("Hello World! This is Lab4 Ktor API")
            }
            get("/hello/{name}") {
                val name = call.parameters["name"]
                call.respondText("Hello, $name!")
            }
            get("/api/info") {
                call.respond(mapOf(
                    "lab" to 4,
                    "project" to "Ktor HelloWorld REST API",
                    "status" to "running"
                ))
            }
        }
    }.start(wait = true)
}
