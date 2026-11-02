plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.titanium.face"; compileSdk = 35
 defaultConfig { applicationId = "com.titanium.face"; minSdk = 28; targetSdk = 35; versionCode = 50; versionName = "0.50" }
 signingConfigs {
   getByName("debug") {
     val signingPath = System.getenv("TITANIUM_SIGNING_FILE")
     if (!signingPath.isNullOrBlank()) {
       storeFile = file(signingPath)
       storePassword = System.getenv("TITANIUM_SIGNING_STORE_PASSWORD")
       keyAlias = System.getenv("TITANIUM_SIGNING_KEY_ALIAS")
       keyPassword = System.getenv("TITANIUM_SIGNING_KEY_PASSWORD")
     }
   }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
 implementation("org.conscrypt:conscrypt-android:2.5.3")
 implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
