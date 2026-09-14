plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.bonamassa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.bonamassa.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val apiUrl = providers.gradleProperty("bonamassaApiUrl").orElse("").get()
        val storeSlug = providers.gradleProperty("bonamassaStoreSlug").orElse("bonamassa").get()
        require(apiUrl.matches(Regex("[A-Za-z0-9:/._-]*"))) { "bonamassaApiUrl inválida" }
        require(storeSlug.matches(Regex("[a-z0-9-]{1,60}"))) { "bonamassaStoreSlug inválida" }
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "STORE_SLUG", "\"$storeSlug\"")
        buildConfigField("boolean", "DEMO_MODE", "false")
    }

    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug {
            buildConfigField("boolean", "DEMO_MODE", providers.gradleProperty("bonamassaDemo").orElse("false").get().toBoolean().toString())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":client"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
