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
    mainClass.set("ru.mirea.robocompetition.bot.client.RunAllBotsKt")
}

tasks.register<JavaExec>("runAllBots") {
    group = "arena"
    description = "Запустить всех 4 ботов в одном процессе"
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
