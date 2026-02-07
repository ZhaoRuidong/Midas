plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("io.freefair.lombok") version "8.11"
}

group = "org.flymars.devtools"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        local("/Users/zhaord/Applications/IntelliJ IDEA.app")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:

        composeUI()

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.modules.platform")
    }

    // Lombok dependencies
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")

    // Jakarta Mail for email functionality (using latest secure version)
    implementation("org.eclipse.angus:angus-mail:2.0.3")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.30387"
        }

        changeNotes = """
            <h3>Initial Release v1.0.0</h3>
            <ul>
                <li>GitLab REST API integration for fetching commits</li>
                <li>Support for multiple GitLab instances (self-hosted and GitLab.com)</li>
                <li>Cross-project weekly report generation</li>
                <li>AI-powered analysis (OpenAI, Claude, Zhipu AI)</li>
                <li>Daily notes feature for enhanced reports</li>
                <li>Multi-language report support (Chinese/English)</li>
                <li>SMTP email delivery with scheduling</li>
                <li>IntelliJ Compose UI for modern user experience</li>
            </ul>
        """.trimIndent()
    }

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
        options.compilerArgs.add("-parameters")
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
