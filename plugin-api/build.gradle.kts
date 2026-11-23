plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

// The plugin API: the types a plugin and PenLab agree on (see docs/plugin-api-spec.md). Plain data
// and interfaces only; no PenLab code. Only the in-process `Panel` needs Compose.
android {
  namespace = "dev.penlab.plugin"
  compileSdk = 36
  defaultConfig {
    minSdk = 30
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = false
  }
  testOptions {
    // The data classes hold android.graphics types (RectF, Bitmap); the stubs must not throw so
    // plain-data tests can build them.
    unitTests.isReturnDefaultValues = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  api(libs.androidx.compose.runtime)
  testImplementation(libs.junit)
}
