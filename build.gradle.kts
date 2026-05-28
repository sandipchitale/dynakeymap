plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "dev.sandipchitale"
version = "1.0.59"

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2026.1") {
            useInstaller = false
            useCache = true
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // PDF generation
    implementation("org.apache.pdfbox:pdfbox:2.0.30")
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
}

intellijPlatform {
    buildSearchableOptions = false

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("261.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(providers.gradleProperty("intellijPublishToken"))
    }
}

