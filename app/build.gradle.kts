import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.pontopj"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.pontopj"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // Chave fixa (pontopj.keystore): toda versão gerada tem a mesma assinatura, então o APK novo
    // atualiza o app instalado sem apagar os registros. É uma chave de uso pessoal, não da Play Store.
    signingConfigs {
        create("pontopj") {
            storeFile = file("pontopj.keystore")
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Sincronização com o relógio (Wear OS).
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
