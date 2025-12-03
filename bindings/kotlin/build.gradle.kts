plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.zcash"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // JNA for FFI bindings
    implementation("net.java.dev.jna:jna:5.14.0")

    // secp256k1 for signing utilities (Bitcoin-kmp includes secp256k1)
    implementation("fr.acinq.secp256k1:secp256k1-kmp:0.15.0")
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.15.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()

    // JNA automatically loads from src/main/resources/<platform>/
    // No need to set jna.library.path when library is in resources
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("t2z")
                description.set("Kotlin/JVM bindings for t2z (Transparent to Zcash) library - enabling transparent Zcash wallets to send shielded Orchard outputs via PCZT")
                url.set("https://github.com/gstohl/t2z")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        name.set("Dominik Gstöhl")
                    }
                }
            }
        }
    }
}
