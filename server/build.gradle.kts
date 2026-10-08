import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The lead backend. Runs the same eligibility engine as the app (from :core).
// Build:  ./gradlew :server:installDist   → server/build/install/server/bin/server
// Run:    see server/README.md
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

application {
    mainClass.set("com.yourco.lending.server.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Ktor 3.3.x is built on Kotlin 2.2, matching the rest of the project.
val ktor = "3.3.3"

dependencies {
    implementation(project(":core"))

    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-rate-limit:$ktor")
    implementation("io.ktor:ktor-server-body-limit:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("ch.qos.logback:logback-classic:1.5.38")

    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("junit:junit:4.13.2")
}
