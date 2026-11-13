plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
    jacoco
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor 核心依赖
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.4.11")



    // Lab 8: 协程并发数据处理
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Lab 10: JWT 认证与密码哈希
    implementation("io.ktor:ktor-server-auth-jvm:2.3.12")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.3.12")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.mindrot:jbcrypt:0.4")

    // Lab 9: 单元测试与集成测试
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.12")
    testImplementation("io.mockk:mockk:1.13.9")
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")

    // Lab 12: 缓存、统一错误处理、请求日志、监控指标
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.12")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.12")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:2.3.12")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

application {
    mainClass.set("Lab7ApplicationKt")
}

// Lab 11 - Step 1：胖 JAR（把全部 runtime 依赖打进单个 jar，容器内可 java -jar 直接运行）
tasks.jar {
    manifest {
        // 入口为 Lab12 的完整微服务（缓存 + 健康检查 + 监控）
        attributes["Main-Class"] = "Lab12ApplicationKt"
        attributes["Implementation-Version"] = project.version.toString()
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 依赖 jar 中的签名文件会让胖 JAR 启动时报 Invalid signature file digest，必须剔除
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Lab 8：协程并发性能测量
tasks.register<JavaExec>("runLab8Perf") {
    group = "lab"
    description = "运行 Lab8 并发 Dashboard 服务的性能测量"
    mainClass.set("Lab8PerformanceKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Lab 8：Dashboard REST 端点（8081，无需数据库）
tasks.register<JavaExec>("runLab8App") {
    group = "lab"
    description = "启动 Lab8 Dashboard REST 服务（端口 8081）"
    mainClass.set("Lab8ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Lab 10：JWT 认证服务（8082，内存仓库，无需数据库）
tasks.register<JavaExec>("runLab10App") {
    group = "lab"
    description = "启动 Lab10 JWT 认证演示服务（端口 8082）"
    mainClass.set("Lab10ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Lab 12：完整微服务（端口 8080 或环境变量 PORT；数据库不可用时自动降级为内存仓库）
tasks.register<JavaExec>("runLab12App") {
    group = "lab"
    description = "启动 Lab12 完整微服务（缓存 + 健康检查 + Prometheus 指标）"
    mainClass.set("Lab12ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    // 允许通过 -Pport=9090 覆盖端口
    (project.findProperty("port") as String?)?.let { environment("PORT", it) }
}
