plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    id("com.vanniktech.maven.publish") version "0.30.0"
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("io.github.gstohl", "t2z-kotlin", version.toString())

    pom {
        name.set("t2z-kotlin")
        description.set("Kotlin/JVM bindings for t2z (Transparent to Zcash) library - enabling transparent Zcash wallets to send shielded Orchard outputs via PCZT")
        url.set("https://github.com/gstohl/t2z")
        inceptionYear.set("2024")

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
