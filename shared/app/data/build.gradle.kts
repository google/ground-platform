/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

// The data layer of shared/app (see docs/technical/client/modules.md): repository implementations,
// the local store and its SQLDelight database, the sync engine, the user data service client, the
// device and platform clients, and the Koin modules that build them (platformModule, dataModule).
// No Compose, no strings, and no ViewModels: none of them is on this module's classpath.
plugins {
  id("collectone.kmp.android")
  id("collectone.detekt")
  id("collectone.kover")
  alias(libs.plugins.sqldelight)
}

val iosEnabled = providers.gradleProperty("collectone.ios").orNull == "true"

kotlin {
  if (iosEnabled) {
    iosArm64()
    iosSimulatorArm64()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.app.domain)
        // The client logger, re-exported so every module above logs through the same setup.
        api(libs.kermit)
        api(libs.koin.core)
        api(libs.ktor.client.core)
        api(libs.sqldelight.runtime)
        implementation(libs.sqldelight.async.extensions)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.ktor.client.cio)
        implementation(libs.androidx.lifecycle.process)
        implementation(libs.sqldelight.android.driver)
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(libs.ktor.client.cio)
        implementation(libs.sqldelight.sqlite.driver)
      }
    }
    val wasmJsMain by getting {
      dependencies {
        implementation(libs.kotlinx.browser)
        implementation(libs.ktor.client.js)
        implementation(libs.sqldelight.web.worker.driver)
        // SQLite for the worker that runs the local database in the browser
        // (web/sqlite-worker/sqlite.worker.js; apps that open the database bundle it).
        implementation(npm("@sqlite.org/sqlite-wasm", libs.versions.sqlite.wasm.get()))
      }
    }
    if (iosEnabled) {
      iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
        implementation(libs.sqldelight.native.driver)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.testing)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.ktor.client.mock)
        implementation(libs.koin.test)
        // Writes the user data service's proto JSON seed (see UserDataSeedSnapshotTest).
        implementation(libs.wire.moshi.adapter)
      }
    }
  }
}

// The local database (src/commonMain/sqldelight/): the sync engine's durable state on the device.
// Queries are generated as suspend functions so the same code runs on the browser's asynchronous
// worker driver; see docs/technical/client/local-data-store.md.
sqldelight {
  databases {
    create("LocalDatabase") {
      packageName.set("org.openforis.collectone.v2.app.data.datasource.local.db")
      generateAsync.set(true)
      // The schema of every version, so verifySqlDelightMigration checks each migration (.sqm).
      schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
      verifyMigrations.set(true)
    }
  }
}

// `./gradlew :shared:app:data:jvmTest -PregenerateLibrarySeed` rewrites the global library seed
// files (shared/assets/library/*.textproto) from the app's Kotlin copy; see GlobalLibrarySeedTest.
// `./gradlew :shared:app:data:jvmTest -PregenerateUserDataSeed` rewrites the server's sample seed
// (server/src/main/resources/seed/user_data_seed.json) from the fixtures in app-testing; see
// UserDataSeedSnapshotTest.
tasks.withType<Test>().configureEach {
  for (flag in listOf("regenerateLibrarySeed", "regenerateUserDataSeed")) {
    if (project.hasProperty(flag)) {
      systemProperty(flag, "true")
      outputs.upToDateWhen { false }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.app.data" }
