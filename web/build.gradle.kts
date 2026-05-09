plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

val ktorVersion = "3.2.0"

dependencies {
    implementation(project(":protocol"))
    implementation(project(":server"))

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.5.13")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("ru.mirea.robocompetition.web.MainKt")
}

tasks.register<JavaExec>("runArena") {
    group = "arena"
    description = "Запустить arena (TCP-сервер ботов + HTTP/WS для фронта)"
    mainClass.set("ru.mirea.robocompetition.web.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
