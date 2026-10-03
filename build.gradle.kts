plugins {
    java
}

group = "de.airgalaxie.airmap"
version = libs.versions.pluginVersion.get()

repositories {
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://repo.mikeprimm.com/")
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly(libs.dynmapCoreApi)
    compileOnly(libs.spigotApi)
    implementation(libs.citizensApi)
}

tasks.compileJava {
    options.release.set(libs.versions.javaRelease.get().toInt())
}

layout.buildDirectory.set(layout.projectDirectory.dir("target"))

val pluginVersion = version.toString()
val apiVersion = libs.versions.apiVersion.get()

tasks.jar {
    destinationDirectory.set(layout.projectDirectory.dir("target"))
}

tasks.processResources {
    filesMatching("*.yml") {
        expand("project" to mapOf("version" to pluginVersion, "apiVersion" to apiVersion))
    }
    filesMatching("*.txt") {
        expand("project" to mapOf("version" to pluginVersion))
    }
}
