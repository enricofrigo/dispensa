plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "eu.frigo.dispensa.sync.local"
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
    implementation(project(":sync-core"))
    implementation(project(":dbcore"))

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.guava)
    implementation(libs.rxjava)
    implementation(libs.androidx.preference)
    implementation(libs.converter.gson)

    testImplementation(libs.junit)
    testImplementation(libs.ext.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
