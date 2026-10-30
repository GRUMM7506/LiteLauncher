import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Ключ публичных сборок хранится вне репозитория. Без него и с -PdebugSigned подписываем debug-ключом.
val keystoreFile = rootProject.file("keystore.properties")
val useReleaseKey = keystoreFile.exists() && !project.hasProperty("debugSigned")

android {
    namespace = "com.grumm.litelauncher"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.grumm.litelauncher"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (useReleaseKey) {
            val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (useReleaseKey) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
}
