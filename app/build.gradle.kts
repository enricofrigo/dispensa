plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.room)
}
android {
    namespace = "eu.frigo.dispensa"
    compileSdk = 36
    buildFeatures{
        buildConfig = true
    }

    defaultConfig {
        applicationId = "eu.frigo.dispensa"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "0.1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensionList.addAll( listOf("store"))

    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("fdroid"){
            dimension = "store"
            versionNameSuffix = "-fdroid"
        }
    }


    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    lint {
        disable.add("UnsafeOptInUsageError")
    }
    bundle{
        language{
            enableSplit = false
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.activity)
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
    implementation(libs.cardview)
    implementation(libs.media3.common)
    implementation(libs.swiperefreshlayout)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.converter.moshi)
    implementation(libs.androidx.camera.view)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.glide)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.balloon)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    annotationProcessor(libs.room.compiler)

    "fdroidImplementation"(libs.zxing.android.embedded)
    "playImplementation"(libs.play.services.mlkit.barcode.scanning)
    "playImplementation"(libs.text.recognition)

}