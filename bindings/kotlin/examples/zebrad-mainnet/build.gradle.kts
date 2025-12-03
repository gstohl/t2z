plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.zcash.t2z.examples"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.zcash:t2z")
    implementation("fr.acinq.secp256k1:secp256k1-kmp:0.15.0")
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.15.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.zcash.t2z.examples.DemoKt")
}

tasks.withType<JavaExec> {
    // JNA automatically loads from resources/<platform>/ in the t2z library
}
