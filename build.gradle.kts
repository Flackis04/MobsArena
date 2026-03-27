import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    application
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("xyz.jpenilla.run-paper") version "2.1.0"
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.helpch.at/releases")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("me.clip:placeholderapi:2.12.2")

    implementation("dev.triumphteam:triumph-gui:3.1.5")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    assemble {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    runServer {
        minecraftVersion("1.21.11")
    }

    named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    withType<ShadowJar> {
        archiveClassifier.set("")
        relocate("dev.triumphteam.gui", "com.example.test")
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("TestPluginKt")
}
