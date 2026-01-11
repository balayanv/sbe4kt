plugins {
   kotlin("jvm") version "2.3.0"
   id("com.google.devtools.ksp") version "2.3.4"
   application
   idea
}

kotlin {
   jvmToolchain(25)
}

ksp {
   arg("kspLogging", "true")
}

repositories {
   mavenCentral()
}

dependencies {
   ksp(project(":sbe4kt"))

   implementation(project(":sbe4kt"))
   implementation("org.agrona:agrona:1.20.0")
   testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named("clean") {
   doLast {
      delete("src/main/resources/sbe-schema.xml")
      delete("src/test/resources/sbe-schema.xml")
   }
}

application {
   mainClass.set("example.ExampleKt")
}

sourceSets.main {
   java.srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin"))
   java.srcDir(layout.buildDirectory.dir("generated/sbe/main/java"))
   java.srcDir("src/main/kotlin")
}

sourceSets.test {
   java.srcDir(layout.buildDirectory.dir("generated/ksp/test/kotlin"))
   java.srcDir(layout.buildDirectory.dir("generated/sbe/test/java"))
   java.srcDir("src/test/kotlin")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    dependsOn("kspKotlin")
}

