plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
    signing
}

group = "io.github.gstohl"
version = System.getenv("VERSION")?.removePrefix("v") ?: "0.1.0"

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
            artifactId = "t2z-kotlin"
            from(components["java"])

            pom {
                name.set("t2z-kotlin")
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
                        id.set("gstohl")
                        name.set("Dominik Gstöhl")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/gstohl/t2z.git")
                    developerConnection.set("scm:git:ssh://github.com/gstohl/t2z.git")
                    url.set("https://github.com/gstohl/t2z")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
