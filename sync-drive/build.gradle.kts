plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "eu.frigo.dispensa.sync.drive"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensionList.addAll(listOf("store"))

    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("fdroid") {
            dimension = "store"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":sync-core"))
    implementation(project(":dbcore"))

    "playImplementation"("com.google.android.gms:play-services-auth:21.3.0")
    "playImplementation"("com.google.api-client:google-api-client-android:2.7.0")
    "playImplementation"("com.google.apis:google-api-services-drive:v3-rev20240730-2.0.0")
    "playImplementation"("com.google.http-client:google-http-client-gson:1.46.0")

    implementation(libs.converter.gson)
    implementation(libs.androidx.preference.ktx)
    "testPlayImplementation"(libs.junit)
}
