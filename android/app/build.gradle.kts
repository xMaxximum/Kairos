import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load version from properties file
val versionPropsFile = file("../../version.properties")
val versionProperties = Properties()
if (versionPropsFile.exists()) {
    versionProperties.load(FileInputStream(versionPropsFile))
}
val currentVersionCode = (versionProperties["versionCode"] as String).toInt()
val currentVersionName = versionProperties["versionName"] as String? ?: "0.1.0"
val releaseKeystoreFile = file("../maxximum.keystore")
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEYSTORE_KEY_ALIAS") ?: "maxximum_key"
val hasReleaseSigning = releaseKeystoreFile.exists() && !releaseStorePassword.isNullOrBlank()

android {
    namespace = "com.maxximum.kairos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maxximum.kairos"
        minSdk = 34
        targetSdk = 35
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Coil (Image Loading)
    implementation(libs.coil.compose)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // JSON
    implementation(libs.google.code.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Task to auto-increment version code and patch version
tasks.register("incrementVersionCode") {
    doLast {
        // version.properties is kept at the repository root so other platforms can read it
        val versionPropsFile = file("../../version.properties")
        val versionProperties = Properties()
        if (versionPropsFile.exists()) {
            versionProperties.load(FileInputStream(versionPropsFile))
        }

        // Increment patch version (last number in semantic versioning)
        val currentVersionName = versionProperties["versionName"] as String? ?: "0.1.0"
        val versionParts = currentVersionName.split(".").toMutableList()
        val patch = versionParts.last().toInt()
        versionParts[versionParts.size - 1] = (patch + 1).toString()
        val newVersionName = versionParts.joinToString(".")

        // Increment versionCode (guarding against nulls)
        val currentCodeStr = versionProperties["versionCode"] as String?
        val currentCode = currentCodeStr?.toIntOrNull() ?: 0
        val newCode = currentCode + 1

        versionProperties["versionName"] = newVersionName
        versionProperties["versionCode"] = newCode.toString()
        versionProperties.store(versionPropsFile.outputStream(), "Auto-incremented version")
        println("Version incremented: $currentVersionName -> $newVersionName (code: $currentCode -> $newCode)")
    }
}

// Hook the increment task to run after assembleRelease (after Android plugin creates tasks)
afterEvaluate {
    tasks.named("assembleRelease").configure {
        finalizedBy("incrementVersionCode")
    }
}
