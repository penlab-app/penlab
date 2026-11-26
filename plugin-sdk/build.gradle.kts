plugins {
  alias(libs.plugins.android.library)
}

// The PenLab plugin SDK: what a plugin APP (a separate app PenLab finds and talks to) needs, and
// what PenLab itself shares with it: the AIDL interface, the JSON wire format, the service base
// class and the helpers for screen activities. Plain Android, no Compose, no PenLab code, so a plugin
// APK stays tiny. See docs/plugin-bridge.md for the contract.
android {
  namespace = "dev.penlab.sdk"
  compileSdk = 36
  defaultConfig {
    minSdk = 30
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    aidl = true
    buildConfig = false
  }
  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  // JSON is org.json from Android itself; the JVM tests need the real implementation (android.jar's is a stub).
  testImplementation(libs.junit)
  testImplementation(libs.org.json)
}
