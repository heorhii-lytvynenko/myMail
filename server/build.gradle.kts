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
    implementation(libs.auth0.springboot.api)
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.springdoc)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    runtimeOnly(libs.postgresql)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.test)
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

                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1)
                }

                key to value
            }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment(loadDotEnv())
}