plugins {
    id("com.android.application")
}

// Version lives in BuildInfo.java so the Gradle and on-device (build.sh) builds always agree.
val buildInfo = file("src/main/java/app/nn/agent/BuildInfo.java").readText()
val appVersionName = Regex("""VERSION = "([^"]+)"""").find(buildInfo)!!.groupValues[1]
val appVersionCode = Regex("""VERSION_CODE = (\d+)""").find(buildInfo)!!.groupValues[1].toInt()

android {
    // proot for the built-in Linux ships as native libraries and must be extracted to disk to run
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    namespace = "app.nn.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.nn.agent"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Release signing: uses NN_KEYSTORE env if provided, otherwise embedded keystore/nn.jks
    val defaultKeystore = file("../keystore/nn.jks")
    val envKeystore = System.getenv("NN_KEYSTORE")
    val keystoreFile = if (envKeystore != null) file(envKeystore) else if (defaultKeystore.exists()) defaultKeystore else null
    val keystorePass = System.getenv("NN_KEYSTORE_PASSWORD") ?: "nnagent2026"
    val keyAliasName = System.getenv("NN_KEY_ALIAS") ?: "nn"
    val keyPass = System.getenv("NN_KEY_PASSWORD") ?: keystorePass

    signingConfigs {
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    // github: the release on GitHub, which updates itself from GitHub releases.
    // fdroid: built and signed by F-Droid from this source; the same app without the self-updater (F-Droid updates it).
    flavorDimensions += "store"
    productFlavors {
        create("github") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    // No Google-only dependency blob and no git details in the APK: F-Droid requires the first, and both keep the
    // fdroid APK the same whoever builds it (F-Droid's reproducible build compares its APK with the one on GitHub).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            vcsInfo.include = false
            if (System.getenv("NN_UNSIGNED") == null && keystoreFile != null && keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }
}
