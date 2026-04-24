plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.plantvisreborn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.plantvisreborn"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Не сжимать .tflite модели
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)

    // TensorFlow Lite — open-source (Apache 2.0), github.com/tensorflow/tensorflow
    implementation(libs.tflite.task.vision)
    implementation(libs.tflite.gpu.delegate)
    implementation(libs.tflite.gpu)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}