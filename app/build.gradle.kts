plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "eu.frigo.dispensa"
    compileSdk = 36
    buildFeatures{
        buildConfig = true
    }

    defaultConfig {
        applicationId = "eu.frigo.dispensa"
        minSdk = 27
        targetSdk = 35
        versionCode = 22
        versionName = "0.1.12"

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
        resources{
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
        jniLibs {
            useLegacyPackaging = false
        }
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
    implementation(project(":dbcore"))
    implementation(project(":sync-core"))
    implementation(project(":sync-webdav"))
    implementation(project(":sync-local"))
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
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.6")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    "fdroidImplementation"(libs.zxing.android.embedded)
    "playImplementation"(project(":sync-drive"))
    "playImplementation"(libs.play.services.auth)
    "playImplementation"(libs.play.services.mlkit.barcode.scanning)
    "playImplementation"(libs.text.recognition)

}