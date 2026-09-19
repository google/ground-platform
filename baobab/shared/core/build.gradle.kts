/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
plugins {
  kotlin("multiplatform") version "2.3.21"
  id("com.squareup.wire") version "7.0.3"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  iosArm64()
  iosSimulatorArm64()
  iosX64()

  js(IR) {
    browser()
    nodejs()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    nodejs()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("com.squareup.wire:wire-runtime:7.0.3")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
      }
    }
  }
}

wire {
  @Suppress("UNCHECKED_CAST")
  (javaClass.superclass
      .getDeclaredField("permitPackageCycles")
      .apply { isAccessible = true }
      .get(this) as org.gradle.api.provider.Property<Boolean>)
    .set(true)
  sourcePath {
    srcDir("../../")
    include("shared/protos/forms/*.proto")
    include("shared/protos/data/*.proto")
    include("shared/protos/survey/*.proto")
  }
  sourcePath {
    srcJar("com.google.api.grpc:proto-google-common-protos:2.48.0")
    include("google/type/date.proto")
    include("google/type/timeofday.proto")
  }
  kotlin { boxOneOfsMinSize = 5000 }
}

tasks
  .matching { it.name == "generateCommonMainProtos" }
  .configureEach {
    dependsOn(tasks.matching { it.name == "transformCommonMainDependenciesMetadata" })
  }

tasks
  .matching {
    it.name != "generateCommonMainProtos" &&
      it.name != "transformCommonMainDependenciesMetadata" &&
      it.name != "kmpPartiallyResolvedDependenciesChecker" &&
      it.name != "checkKotlinGradlePluginConfigurationErrors"
  }
  .configureEach { mustRunAfter("generateCommonMainProtos") }
