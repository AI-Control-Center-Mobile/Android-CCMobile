plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ivnsrg.aicontrolcentre.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
