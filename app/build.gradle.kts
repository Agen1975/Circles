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
        versionCode = 3
        versionName = "2.0.0"
        manifestPlaceholders["admobAppId"] = "ca-app-pub-8769003368118760~7668859499"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Google's public test ad units: safe to click, never earn money.
            // Build with -PnoAds to switch ads off for testing.
            val ads = !project.hasProperty("noAds")
            buildConfigField("String", "AD_INTERSTITIAL",
                if (ads) "\"ca-app-pub-3940256099942544/1033173712\"" else "\"\"")
            buildConfigField("String", "AD_REWARDED",
                if (ads) "\"ca-app-pub-3940256099942544/5224354917\"" else "\"\"")
        }
        release {
            // Real ad unit IDs from AdMob go here. Empty = ads stay off in release builds.
            buildConfigField("String", "AD_INTERSTITIAL", "\"\"")
            buildConfigField("String", "AD_REWARDED", "\"\"")
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
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing:8.3.0")
    testImplementation("junit:junit:4.13.2")
}
