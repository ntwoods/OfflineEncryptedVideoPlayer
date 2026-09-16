
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ntwoods.offlineplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ntwoods.offlineplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig field holding the Base64 AES key (demo only)
        buildConfigField(
            "String",
            "AES_KEY_B64",
            "\"hX4TrH862GGimorb3X7uqWcau34NNnEEuLvH43yNeHQ=\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    // Encrypted media must remain uncompressed inside the APK so the custom
    // ExoPlayer DataSource can seek directly to requested ciphertext offsets.
    androidResources {
        noCompress += "enc"
    }

    dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.appcompat:appcompat:1.7.0")
        implementation("androidx.viewpager2:viewpager2:1.1.0")
        implementation("com.google.android.material:material:1.12.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // ExoPlayer
        implementation("androidx.media3:media3-exoplayer:1.4.1")
        implementation("androidx.media3:media3-ui:1.4.1")
        implementation("androidx.media3:media3-common:1.4.1")

        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    }
}
