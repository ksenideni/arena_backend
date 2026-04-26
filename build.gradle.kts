plugins {
    kotlin("jvm") version "2.3.10"
    application
}

application {
    mainClass.set("ru.mirea.robocompetition.MainKt")
}

group = "ru.mirea.robocompetition"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// === Удобные таски запуска ===

tasks.register<JavaExec>("runServer") {
    group = "arena"
    description = "Запустить сервер соревнований на порту 9000"
    mainClass.set("ru.mirea.robocompetition.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runAllBots") {
    group = "arena"
    description = "Запустить всех 4 ботов одной командой (для демо)"
    mainClass.set("ru.mirea.robocompetition.bot.client.RunAllBotsKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runHorizontal") {
    group = "arena"
    description = "Запустить HorizontalBot (имя через --args=\"Alice\")"
    mainClass.set("ru.mirea.robocompetition.bot.client.HorizontalBotClientKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runVertical") {
    group = "arena"
    description = "Запустить VerticalBot (имя через --args=\"Bob\")"
    mainClass.set("ru.mirea.robocompetition.bot.client.VerticalBotClientKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runRandom") {
    group = "arena"
    description = "Запустить RandomBot (имя через --args=\"Charlie\")"
    mainClass.set("ru.mirea.robocompetition.bot.client.RandomBotClientKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runGreedy") {
    group = "arena"
    description = "Запустить GreedyBot (имя через --args=\"Diana\")"
    mainClass.set("ru.mirea.robocompetition.bot.client.GreedyBotClientKt")
    classpath = sourceSets["main"].runtimeClasspath
}
