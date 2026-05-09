plugins {
    kotlin("jvm")
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
