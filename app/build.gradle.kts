plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "dev.penlab.app"
  compileSdk = 36
  defaultConfig {
    applicationId = "dev.penlab.app"
    minSdk = 30
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    // The build for daily use: optimised by R8 and not debuggable, which makes Compose and
    // the ink code noticeably faster than a debug build. Signed with the laptop's debug key
    // so it installs over the debug build without a keystore to look after; that is fine for
    // a private app, and must change before the app is ever published.
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  testOptions {
    // Local tests run on the JVM against android.jar stubs: let android.util.Log and RectF be
    // harmless no-ops instead of throwing, so plain logic that logs can be tested.
    unitTests.isReturnDefaultValues = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  // The plugin API (plain data + interfaces); built-in plugins depend on this alone.
  implementation(project(":plugin-api"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // On-device handwriting recognition for search (free, nothing leaves the tablet)
  implementation(libs.mlkit.digital.ink)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Ink: low-latency stylus strokes (Jetpack Ink) + pen motion prediction
  implementation(libs.androidx.ink.authoring)
  implementation(libs.androidx.ink.brush)
  implementation(libs.androidx.ink.geometry)
  implementation(libs.androidx.ink.rendering)
  implementation(libs.androidx.ink.strokes)
  implementation(libs.androidx.input.motionprediction)

}
