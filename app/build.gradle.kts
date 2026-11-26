import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// The shared PenLab signing key lives OUTSIDE the repo, so every maintainer's build installs over
// every other's (Android refuses an update signed with a different key). Point PENLAB_KEYSTORE at
// a keystore.properties file (storeFile, storePassword, keyAlias, keyPassword), or keep it at
// ~/.config/penlab/keystore.properties. Without it, builds fall back to the local debug key.
val penlabKeyProps = Properties().apply {
  val f = file(System.getenv("PENLAB_KEYSTORE") ?: "${System.getProperty("user.home")}/.config/penlab/keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
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

  signingConfigs {
    if (penlabKeyProps.getProperty("storeFile") != null) {
      create("penlab") {
        storeFile = file(penlabKeyProps.getProperty("storeFile"))
        storePassword = penlabKeyProps.getProperty("storePassword")
        keyAlias = penlabKeyProps.getProperty("keyAlias")
        keyPassword = penlabKeyProps.getProperty("keyPassword")
      }
    }
  }
  val shared = signingConfigs.findByName("penlab")

  buildTypes {
    // Debug and release both use the shared key when it's present, so they install over each other.
    debug {
      if (shared != null) signingConfig = shared
    }
    // The build for daily use: optimised by R8 and not debuggable, which makes Compose and
    // the ink code noticeably faster than a debug build.
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = shared ?: signingConfigs.getByName("debug")
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
  // The bridge to plugin APPS (AIDL, wire format, helpers), shared with them.
  implementation(project(":plugin-sdk"))

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
  implementation(libs.phosphor.icon)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // JSON tests need the real org.json (android.jar's is a stub).
  testImplementation(libs.org.json)

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
