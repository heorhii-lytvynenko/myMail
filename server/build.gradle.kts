plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependencyManagement)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

springBoot {
    mainClass.set("com.mymail.MailApplication")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":domain"))

    // Lombok
    compileOnly(libs.lombok.core)
    annotationProcessor(libs.lombok.core)

    // MapStruct
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.mapstruct.lombok.binding)

    // Spring
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.restclient)
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.data.jpa)
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // OpenAPI
    implementation(libs.springdoc)


    // Google
    implementation(libs.google.api.client)
    implementation(libs.google.oauth.client)
    implementation(libs.google.gmail)

    // Email
    implementation(libs.resend)
    implementation(libs.jakarta.mail)

    // Database
    runtimeOnly(libs.postgresql)

    // Testing
    testImplementation(libs.spring.boot.test)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly(libs.lombok.core)
    testAnnotationProcessor(libs.lombok.core)
}

val loadDotEnv = {
    val envFile = rootProject.file(".env")

    if (!envFile.exists()) {
        emptyMap<String, String>()
    } else {
        envFile.readLines()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                        !trimmed.startsWith("#") &&
                        trimmed.contains("=")
            }
            .associate { line ->
                val index = line.indexOf("=")
                val key = line.substring(0, index).trim()
                var value = line.substring(index + 1).trim()

                if (
                    (value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))
                ) {
                    value = value.substring(1, value.length - 1)
                }

                key to value
            }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment(loadDotEnv())
}
tasks.withType<Test> {
    useJUnitPlatform()
}
