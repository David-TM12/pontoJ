import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // O pacote do código é br.com.pontopj.wear, mas o applicationId é o mesmo do celular:
    // é isso que permite os dois apps conversarem pela camada de dados do Wear OS.
    namespace = "br.com.pontopj.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.pontopj"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // Mesma chave do celular: o Wear OS só liga os dois apps se a assinatura for igual.
    signingConfigs {
        create("pontopj") {
            storeFile = file("../app/pontopj.keystore")
            storePassword = "pontopj123"
            keyAlias = "pontopj"
            keyPassword = "pontopj123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("pontopj")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("pontopj")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Camada de dados do Wear OS (conversa com o celular).
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
