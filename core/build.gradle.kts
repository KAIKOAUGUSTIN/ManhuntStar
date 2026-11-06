// Code that doesn't depend on any platform: no Paper and no Minecraft on the classpath, so anything
// platform-specific that ends up here doesn't compile. The platform modules (paper) build on top of it.

plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Manhunt* - platform-independent code"

// Adventure (texts), SnakeYAML (YAML files) and Gson (JSON) aren't platform code: Paper already has them,
// Minecraft brings Gson, and the mods will bundle the others. core compiles against the versions of the
// oldest supported Paper.
val adventure = libs.versions.adventure.get()
val snakeyaml = libs.versions.snakeyaml.get()
val gson = libs.versions.gson.get()

dependencies {
    compileOnlyApi("net.kyori:adventure-api:$adventure")
    compileOnlyApi("net.kyori:adventure-text-minimessage:$adventure")
    compileOnly("org.yaml:snakeyaml:$snakeyaml")
    compileOnly("com.google.code.gson:gson:$gson")

    testImplementation("net.kyori:adventure-api:$adventure")
    testImplementation("net.kyori:adventure-text-minimessage:$adventure")
    testImplementation("org.yaml:snakeyaml:$snakeyaml")
    testImplementation("com.google.code.gson:gson:$gson")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // tests shared with the platforms: each one runs them against its own implementation
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
}
