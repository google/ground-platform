/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  id("collectone.kmp.android")
  id("collectone.kmp.compose")
  id("collectone.kover")
  // @Serializable navigation routes.
  alias(libs.plugins.kotlin.serialization)
}

// iOS targets are opt-in: `./gradlew -Pcollectone.ios=true :shared:mobile:...` builds the AppMobile
// framework for iosApp.
val iosEnabled = providers.gradleProperty("collectone.ios").orNull == "true"

kotlin {
  if (iosEnabled) {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
      iosTarget.binaries.framework {
        baseName = "AppMobile"
        isStatic = true
        // iosApp implements AppMap's Mapbox view (MapboxIosView) and AppMobileSyncIos's
        // push topic bridge (IosPushTopicBridge) in Swift.
        export(projects.shared.map)
        export(projects.shared.app)
      }
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.core)
        api(projects.shared.ui)
        api(projects.shared.app)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
        implementation(compose.components.resources)
        // Type-safe Compose navigation between the mobile app's screens.
        api(libs.jetbrains.navigation.compose)
        implementation(libs.jetbrains.navigationevent.compose)
      }
    }
    if (iosEnabled) {
      iosMain.dependencies { api(projects.shared.map) }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.uiTesting)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.konsist)
        // Headless Compose UI tests (runComposeUiTest) for interaction regressions.
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.mobile" }

// Generated `Res` accessors for this module's UI strings (src/commonMain/composeResources).
compose.resources {
  publicResClass = false
  packageOfResClass = "org.openforis.collectone.v2.mobile.resources"
}
