// Code that doesn't depend on any platform: no Paper and no Minecraft on the classpath, so anything
// platform-specific that ends up here doesn't compile. The platform modules (paper) build on top of it.

plugins {
    `java-library`
}

description = "Manhunt* - platform-independent code"

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
