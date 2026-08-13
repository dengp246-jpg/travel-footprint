plugins {
    id("com.android.application")
}

val configuredServerUrl = providers.gradleProperty("serverUrl")
    .orElse("")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val serverUrlPreconfigured = providers.gradleProperty("serverUrl")
    .map { it.isNotBlank() }
    .orElse(false)
    .get()

android {
    namespace = "com.example.travelfootprint.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.travelfootprint.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$configuredServerUrl\"")
        buildConfigField("boolean", "SERVER_PRECONFIGURED", serverUrlPreconfigured.toString())
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
