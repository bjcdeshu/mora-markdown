plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseStoreFile = providers.environmentVariable("MORA_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("MORA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("MORA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("MORA_RELEASE_KEY_PASSWORD")

val releaseSigningEnvironment = mapOf(
    "MORA_RELEASE_STORE_FILE" to releaseStoreFile,
    "MORA_RELEASE_STORE_PASSWORD" to releaseStorePassword,
    "MORA_RELEASE_KEY_ALIAS" to releaseKeyAlias,
    "MORA_RELEASE_KEY_PASSWORD" to releaseKeyPassword,
)

android {
    namespace = "de.unbow.mora"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.unbow.mora"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en-rUS", "zh-rCN")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile.orNull
                ?.takeIf(String::isNotBlank)
                ?.let(project::file)
            storePassword = releaseStorePassword.orNull?.takeIf(String::isNotBlank)
            keyAlias = releaseKeyAlias.orNull?.takeIf(String::isNotBlank)
            keyPassword = releaseKeyPassword.orNull?.takeIf(String::isNotBlank)
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyReleaseSigningEnvironment = tasks.register("verifyReleaseSigningEnvironment") {
    group = "verification"
    description = "Fails before a release build when Mora's signing environment is incomplete."
    doNotTrackState("Release signing credentials must never be stored in the build cache.")

    doLast {
        val missingVariables = releaseSigningEnvironment
            .filterValues { provider -> provider.orNull.isNullOrBlank() }
            .keys

        check(missingVariables.isEmpty()) {
            "Release signing is not configured. Missing environment variables: " +
                missingVariables.joinToString()
        }

        val configuredStoreFile = project.file(releaseStoreFile.get())
        check(configuredStoreFile.isFile) {
            "Release keystore does not exist at the configured MORA_RELEASE_STORE_FILE path."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease") {
        dependsOn(verifyReleaseSigningEnvironment)
    }
}

val repositoryThirdPartyNotices = rootProject.layout.projectDirectory.file(
    "THIRD_PARTY_NOTICES.md",
)
val bundledThirdPartyNotices = layout.projectDirectory.file(
    "src/main/assets/THIRD_PARTY_NOTICES.md",
)
val verifyBundledThirdPartyNotices = tasks.register("verifyBundledThirdPartyNotices") {
    group = "verification"
    description = "Checks that the APK-bundled third-party notices match the repository copy."
    inputs.files(repositoryThirdPartyNotices, bundledThirdPartyNotices)

    doLast {
        check(
            repositoryThirdPartyNotices.asFile.readBytes().contentEquals(
                bundledThirdPartyNotices.asFile.readBytes(),
            ),
        ) {
            "app/src/main/assets/THIRD_PARTY_NOTICES.md must match THIRD_PARTY_NOTICES.md."
        }
    }
}

val runtimeThirdPartyNoticeReport = layout.buildDirectory.file(
    "reports/runtime-third-party-notices.txt",
)
val verifyRuntimeThirdPartyNotices = tasks.register("verifyRuntimeThirdPartyNotices") {
    group = "verification"
    description =
        "Checks every resolved Release runtime module against Mora's third-party notices."
    dependsOn(verifyBundledThirdPartyNotices)
    inputs.file(repositoryThirdPartyNotices)
    outputs.file(runtimeThirdPartyNoticeReport)
    outputs.upToDateWhen { false }

    doLast {
        val notices = repositoryThirdPartyNotices.asFile.readText()
        val resolvedModules = configurations.getByName("releaseRuntimeClasspath")
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { component ->
                val identifier = component.id as?
                    org.gradle.api.artifacts.component.ModuleComponentIdentifier
                    ?: return@mapNotNull null
                Triple(identifier.group, identifier.module, identifier.version)
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

        check(resolvedModules.isNotEmpty()) {
            "releaseRuntimeClasspath resolved no external modules; notice audit cannot continue."
        }

        fun noticeMetadata(group: String, module: String): List<String>? = when {
            group.startsWith("androidx.") -> listOf(
                "AndroidX / Jetpack Compose",
                "Apache-2.0",
                "## AndroidX and Jetpack Compose",
            )
            group == "org.jetbrains.kotlin" -> listOf(
                "Kotlin",
                "Apache-2.0",
                "## Kotlin",
            )
            group == "org.jetbrains.kotlinx" && module.startsWith("kotlinx-coroutines-") ->
                listOf(
                    "kotlinx.coroutines",
                    "Apache-2.0",
                    "## kotlinx.coroutines",
                )
            group == "org.jetbrains.kotlinx" &&
                module.startsWith("kotlinx-serialization-") -> listOf(
                "kotlinx.serialization",
                "Apache-2.0",
                "## kotlinx.serialization",
            )
            group == "org.commonmark" -> listOf(
                "commonmark-java",
                "BSD-2-Clause",
                "## commonmark-java",
            )
            group == "org.jspecify" && module == "jspecify" -> listOf(
                "JSpecify",
                "Apache-2.0",
                "## JSpecify",
            )
            group == "com.google.guava" && module == "listenablefuture" -> listOf(
                "Guava ListenableFuture",
                "Apache-2.0",
                "## Guava ListenableFuture",
            )
            group == "org.jetbrains" && module == "annotations" -> listOf(
                "JetBrains annotations",
                "Apache-2.0",
                "## JetBrains annotations",
            )
            else -> null
        }

        val unknownCoordinates = mutableListOf<String>()
        val requiredNoticeMarkers = linkedSetOf<Pair<String, String>>()
        val reportLines = resolvedModules.map { (group, module, version) ->
            val coordinate = "$group:$module:$version"
            val metadata = noticeMetadata(group, module)
            if (metadata == null) {
                unknownCoordinates += coordinate
                "$coordinate -> UNMAPPED"
            } else {
                requiredNoticeMarkers += metadata[2] to metadata[1]
                "$coordinate -> ${metadata[0]} (${metadata[1]})"
            }
        }

        val reportFile = runtimeThirdPartyNoticeReport.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Mora resolved Release runtime third-party notice audit")
                appendLine("Every external module must map to a documented license family.")
                appendLine()
                reportLines.forEach { line -> appendLine(line) }
            },
        )

        check(unknownCoordinates.isEmpty()) {
            "Unmapped Release runtime dependencies:\n" +
                unknownCoordinates.joinToString(separator = "\n") +
                "\nUpdate THIRD_PARTY_NOTICES.md and the verification mapping."
        }

        val missingNoticeEntries = requiredNoticeMarkers.filterNot { (section, license) ->
            notices.contains(section) && notices.contains(license)
        }
        check(missingNoticeEntries.isEmpty()) {
            "THIRD_PARTY_NOTICES.md is missing resolved runtime notice entries: " +
                missingNoticeEntries.joinToString { (section, license) -> "$section ($license)" }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyBundledThirdPartyNotices)
}

tasks.matching { task -> task.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyRuntimeThirdPartyNotices)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    implementation(libs.commonmark.task.list)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
