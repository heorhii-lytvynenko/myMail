plugins {
    java
    alias(libs.plugins.openapi.generator)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:4.1.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.20.0")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.49")
    implementation("org.openapitools:jackson-databind-nullable:0.2.10")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
}

openApiGenerate {
    generatorName.set("spring")

    inputSpec.set("$projectDir/src/api.yaml")

    outputDir.set(
        layout.buildDirectory
            .dir("generated")
            .get()
            .asFile
            .absolutePath
    )

    apiPackage.set("com.mymail.api")
    modelPackage.set("com.mymail.api.model")

    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "skipDefaultInterface" to "true",
            "useBeanValidation" to "true",
            "dateLibrary" to "java8"
        )
    )
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/src/main/java"))
        }
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}