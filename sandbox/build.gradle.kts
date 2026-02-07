val jvmUnsafeAccessArg = "--sun-misc-unsafe-memory-access=allow"
val sbeCompilerMain = "uk.co.real_logic.sbe.SbeTool"

plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

configurations {
    create("sbeCompiler")
}

dependencies {
    implementation("org.agrona:agrona:1.20.0")
    implementation("uk.co.real-logic:sbe-all:1.31.1")
    "sbeCompiler"("uk.co.real-logic:sbe-all:1.31.1")

    implementation("com.lmax:disruptor:4.0.0")

    testImplementation("io.cucumber:cucumber-java:7.29.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.29.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("sandbox.DisruptorExampleKt")
    applicationDefaultJvmArgs = listOf(jvmUnsafeAccessArg)
}

kotlin {
    jvmToolchain(25)
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", layout.buildDirectory.dir("generated/sbe/java"))
        }
    }
}

val sbeGenerate = tasks.register<JavaExec>("sbeGenerate") {
    group = "build"
    description = "Generate SBE Java code from XML schema"

    mainClass.set(sbeCompilerMain)
    classpath = configurations["sbeCompiler"]
    jvmArgs(jvmUnsafeAccessArg)

    val schemaFile = file("src/main/resources/schema.xml")
    val outputDir = file("build/generated/sbe/java")

    inputs.files(schemaFile)
    outputs.dir(outputDir)

    doFirst {
        outputDir.mkdirs()
        if (schemaFile.exists()) {
            systemProperty("sbe.output.dir", outputDir.absolutePath)
            systemProperty("sbe.generate.ir", "true")
            systemProperty("sbe.target.language", "Java")
            systemProperty("sbe.java.generate.package.info", "true")
            args = listOf(schemaFile.absolutePath)
        } else {
            println("SBE schema not found at ${schemaFile.absolutePath}")
            throw StopExecutionException("Schema file not found")
        }
    }

    onlyIf {
        schemaFile.exists()
    }
}

tasks.named("clean") {
    doLast {
        delete("build/generated/sbe")
    }
}

tasks.named("compileJava") {
    dependsOn(sbeGenerate)
}

tasks.named("compileKotlin") {
    dependsOn(sbeGenerate)
}

tasks.test {
    useJUnitPlatform {
        includeEngines("cucumber")
    }
    systemProperty("cucumber.glue", "sandbox")
    systemProperty("cucumber.features", "classpath:features")
}