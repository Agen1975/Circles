plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.game.cosmicbubbles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.game.cosmicbubbles"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
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
            // Live AdMob ad units ("Between games" and "Undo or hint").
            buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-8769003368118760/5017424233\"")
            buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-8769003368118760/4070514934\"")
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
    implementation("androidx.work:work-runtime:2.10.0")
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    testImplementation("junit:junit:4.13.2")
}
