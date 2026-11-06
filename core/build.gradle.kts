// Code that doesn't depend on any platform: no Paper and no Minecraft on the classpath, so anything
// platform-specific that ends up here doesn't compile. The platform modules (paper) build on top of it.

plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Manhunt* - platform-independent code"

// Adventure (texts) and SnakeYAML (YAML files) aren't platform code: Paper already has them, and the
// mods will bundle them. core compiles against the versions of the oldest supported Paper.
val adventure = libs.versions.adventure.get()
val snakeyaml = libs.versions.snakeyaml.get()

dependencies {
    compileOnlyApi("net.kyori:adventure-api:$adventure")
    compileOnlyApi("net.kyori:adventure-text-minimessage:$adventure")
    compileOnly("org.yaml:snakeyaml:$snakeyaml")

    testImplementation("net.kyori:adventure-api:$adventure")
    testImplementation("net.kyori:adventure-text-minimessage:$adventure")
    testImplementation("org.yaml:snakeyaml:$snakeyaml")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // tests shared with the platforms: each one runs them against its own implementation
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
}
