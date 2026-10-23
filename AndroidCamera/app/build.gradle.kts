plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.hdrg.camera"
  compileSdk = 35
  defaultConfig { applicationId = "com.hdrg.camera"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.6.3-rc4"; testInstrumentationRunner = "com.hdrg.camera.CameraSmokeInstrumentation" }
  buildTypes { getByName("debug") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }; getByName("release") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {}
