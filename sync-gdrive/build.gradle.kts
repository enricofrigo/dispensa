plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "eu.frigo.dispensa.sync.gdrive"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(project(":sync-core"))
    implementation(project(":dbcore"))

    implementation(libs.play.services.auth)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.api.client.android)
    implementation(libs.google.http.client.gson)

    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation(libs.androidx.preference)
    implementation(libs.guava)
    implementation(libs.converter.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
