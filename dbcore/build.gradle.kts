plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "eu.frigo.dispensa.dbcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    api(libs.room.runtime)
    api(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.room.rxjava2)
    implementation(libs.room.rxjava3)
    implementation(libs.room.guava)
    annotationProcessor(libs.room.compiler)

    implementation(libs.media3.common)
    implementation(libs.converter.gson)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
}
