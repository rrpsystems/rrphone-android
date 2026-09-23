plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Chave do .rrpprofile, a mesma do desktop, lida de um arquivo fora do
// repositório (profile_key.txt, 64 hex). Procura no projeto Android e, na
// falta, no desktop ao lado (C:\dev\RRPhone\desktop). Sem nenhum dos dois,
// cai na chave de desenvolvimento publicada — que não protege nada, mas deixa
// um clone novo compilar. Arquivos exportados com chaves diferentes não abrem
// do outro lado.
val profileKeyHex: String = run {
    val devKey = "8f2bd1740a63e519c47d36a851be920f27dc68b34e15f78a39c06d24ab50e396"
    val candidates = listOf(rootProject.file("profile_key.txt"), rootProject.file("../desktop/profile_key.txt"))
    val file = candidates.firstOrNull { it.isFile } ?: return@run devKey
    val key = file.readText().filter { !it.isWhitespace() }.lowercase()
    require(key.length == 64 && key.all { it in "0123456789abcdef" }) {
        "${file.path}: a chave precisa ter 64 caracteres hexadecimais"
    }
    key
}

android {
    namespace = "com.rrpsystems.rrphone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.rrpsystems.rrphone"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RRP_PROFILE_KEY_HEX", "\"$profileKeyHex\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Local libs (.aar / .jar)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // Liblinphone SDK
    implementation(libs.linphone.sdk)

    // Networking (Retrofit/OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Navigation Compose
    implementation(libs.androidx.navigation.compose)
}