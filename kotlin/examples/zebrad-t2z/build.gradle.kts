plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.zcash.t2z.examples"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // t2z library (local project dependency via includeBuild substitution)
    implementation("com.zcash:t2z")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // secp256k1 for key management (same as main lib)
    implementation("fr.acinq.secp256k1:secp256k1-kmp:0.15.0")
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.15.0")

    // Bitcoin-style base58check encoding
    implementation("fr.acinq.bitcoin:bitcoin-kmp:0.19.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

// Register tasks for each example
listOf(
    "setup" to "Setup",
    "example1" to "Example1SingleOutput",
    "example2" to "Example2MultipleOutputs",
    "example3" to "Example3MultipleInputs",
    "example4" to "Example4AttackScenario",
    "example5" to "Example5ShieldedOutput",
    "example6" to "Example6MultipleShielded",
    "example7" to "Example7MixedOutputs"
).forEach { (taskName, mainClassName) ->
    tasks.register<JavaExec>(taskName) {
        group = "examples"
        description = "Run $mainClassName"
        mainClass.set("com.zcash.t2z.examples.${mainClassName}Kt")
        classpath = sourceSets["main"].runtimeClasspath

        // Set native library path
        systemProperty("jna.library.path", file("../../../rust/target/release").absolutePath)
    }
}

// Run all examples in sequence
tasks.register("all") {
    group = "examples"
    description = "Run all examples in sequence"
    dependsOn("example1", "example2", "example3", "example4", "example5", "example6", "example7")
}
