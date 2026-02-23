plugins {
    java
    application
}

group = "com.pmfb.gonogo.engine"
version = "0.1.0-SNAPSHOT"

val targetJavaVersion = providers.gradleProperty("gonogoJavaVersion")
    .orElse(JavaVersion.current().majorVersion)
    .map { value ->
        value.substringBefore('.').toIntOrNull()
            ?: throw GradleException(
                "Invalid Java version value '$value'. Use major version like 21 or 25 " +
                    "via -PgonogoJavaVersion=<major>."
            )
    }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.yaml:snakeyaml:2.4")
    implementation("org.jsoup:jsoup:1.19.1")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.pmfb.gonogo.engine.Main"
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.release = targetJavaVersion.get()
}
