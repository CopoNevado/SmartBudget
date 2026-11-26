plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.example.smartbudget.wear"
 compileSdk = 35
 defaultConfig { applicationId = "com.example.smartbudget"; minSdk = 30; targetSdk = 34; versionCode = 1; versionName = "1.0" }
 sourceSets.getByName("main").java.srcDir("../common/src/main/java")
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(libs.play.services.wearable)
 implementation(libs.kotlinx.coroutines.play.services)
 implementation(libs.kotlinx.coroutines.android)
}
