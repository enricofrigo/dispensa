import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "eu.frigo.dispensa"
    compileSdk = 36
    buildFeatures{
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "eu.frigo.dispensa"
        minSdk = 27
        targetSdk = 36
        versionCode = 26
        versionName = "0.1.16"

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


    val releaseStoreFilePath = keystoreProperties.getProperty("storeFile")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: "../sign.jks"
    val releaseStoreFile = rootProject.file(releaseStoreFilePath)
    val releaseStorePassword = keystoreProperties.getProperty("storePassword")
        ?: System.getenv("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")
        ?: System.getenv("RELEASE_KEY_ALIAS")
        ?: "key0"
    val releaseKeyPassword = keystoreProperties.getProperty("keyPassword")
        ?: System.getenv("RELEASE_KEY_PASSWORD")
        ?: releaseStorePassword
    val hasReleaseSigning = releaseStoreFile.exists() && !releaseStorePassword.isNullOrBlank()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
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
            resValue("string", "app_name", "Debug Dispensa")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "FULL"
            }
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
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    lint {
        disable.add("UnsafeOptInUsageError")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "fastlane.screenshots",
                    project.findProperty("fastlane.screenshots") ?: "false"
                )
                it.systemProperty(
                    "fastlane.output.dir",
                    (project.findProperty("fastlane.output.dir")
                        ?: rootProject.file("fastlane/metadata/android").absolutePath).toString()
                )
            }
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
    "playImplementation"(project(":sync-gdrive"))
    implementation(libs.cardview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.glide)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.balloon)
    implementation(libs.guava)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    "fdroidImplementation"(libs.zxing.android.embedded)
    "playImplementation"(libs.play.services.mlkit.barcode.scanning)
    "playImplementation"(libs.text.recognition)
    "playImplementation"(libs.play.services.auth)
}