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
    implementation(project(":sync-core"))
    implementation(project(":dbcore"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation(libs.guava)
    implementation(libs.converter.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
