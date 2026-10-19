plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.hdrg.camera"
  compileSdk = 35
  defaultConfig { applicationId = "com.hdrg.camera"; minSdk = 26; targetSdk = 35; versionCode = 8; versionName = "0.6.2-rc3" }
  buildTypes { getByName("release") { isMinifyEnabled = false } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {}
