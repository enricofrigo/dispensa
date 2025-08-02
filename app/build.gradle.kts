plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "eu.frigo.dispensa"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.frigo.dispensa"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.2"

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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.room.rxjava2)
    implementation(libs.room.rxjava3)
    implementation(libs.room.guava)
    implementation(libs.play.services.analytics.impl)
    implementation(libs.cardview)
    implementation(libs.media3.common)
    implementation(libs.swiperefreshlayout)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.play.services.mlkit.barcode.scanning)
    implementation(libs.converter.moshi)
    implementation(libs.barcode.scanning)
    implementation(libs.androidx.camera.view)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.glide)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    annotationProcessor(libs.room.compiler)
}