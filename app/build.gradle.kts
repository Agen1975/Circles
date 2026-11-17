plugins {
    id("com.android.application")
}

android {
    namespace = "com.game.cosmicbubbles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.game.cosmicbubbles"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
