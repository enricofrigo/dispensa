plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "eu.frigo.dispensa.sync.webdav"
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
    implementation(project(":dbcore"))

    implementation(libs.okhttp)
    implementation(libs.rxjava)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.preference)
    implementation(libs.guava)
    implementation(libs.converter.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
