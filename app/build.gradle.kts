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
        versionCode = 4
        versionName = "0.3.0"
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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
    testImplementation(libs.junit)
}
