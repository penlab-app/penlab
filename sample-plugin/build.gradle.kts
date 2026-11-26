import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
}

// A tiny, complete PenLab plugin APP: copy this module to start your own. It is signed with the same
// shared PenLab key as the app itself (see app/build.gradle.kts), which makes PenLab label it "Trusted"
// and switch it on by itself. Without the key (a fresh clone) it falls back to the local debug key and
// shows up as an ordinary plugin that the user switches on.
val penlabKeyProps = Properties().apply {
  val f = file(System.getenv("PENLAB_KEYSTORE") ?: "${System.getProperty("user.home")}/.config/penlab/keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

android {
  namespace = "dev.penlab.sample"
  compileSdk = 36
  defaultConfig {
    applicationId = "dev.penlab.sample"
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
    debug {
      if (shared != null) signingConfig = shared
    }
    release {
      // Nothing to shrink in a plugin this small; keeping it readable also keeps the sample honest.
      isMinifyEnabled = false
      signingConfig = shared ?: signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    buildConfig = false
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":plugin-sdk"))
}
