plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "eu.frigo.dispensa.sync.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {

    implementation(libs.androidx.preference)
    implementation(libs.room.rxjava3)
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation(libs.converter.gson)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
