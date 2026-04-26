plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":protocol"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("ru.mirea.robocompetition.MainKt")
}

tasks.register<JavaExec>("runServer") {
    group = "arena"
    description = "Запустить сервер соревнований на порту 9000"
    mainClass.set("ru.mirea.robocompetition.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
