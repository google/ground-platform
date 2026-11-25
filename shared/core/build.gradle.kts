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
import org.openforis.collectone.v2.buildlogic.ProtoSchemaDescriptorsFactory
import org.openforis.collectone.v2.buildlogic.isServerOnly

plugins {
  id("collectone.kmp.library")
  id("collectone.detekt")
  id("collectone.kover")
  alias(libs.plugins.wire)
}

kotlin {
  if (!isServerOnly()) {
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js {
      browser()
      nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { nodejs() }
  }

  sourceSets {
    val commonMain by getting {
      kotlin.srcDir(
        files(layout.buildDirectory.dir("generated/source/wire"))
          .builtBy("generateCommonMainProtos")
      )
      kotlin.srcDir(
        files(layout.buildDirectory.dir("generated/source/protoSchema"))
          .builtBy("generateCommonMainProtos")
      )
      dependencies {
        api(libs.wire.runtime)
        api(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.junit.jupiter.params)
      }
    }
  }
}

// Stage `../protos` under `build/stagedProtos/shared/protos` so `import "shared/protos/..."`
// resolves without pointing `srcDir` at `../../` (the repository root, which contains `build/`
// directories and causes Gradle input-overlap / implicit-dependency failures across targets).
val stageProtos by
  tasks.registering(Sync::class) {
    from("../protos")
    into(layout.buildDirectory.dir("stagedProtos/shared/protos"))
  }

wire {
  @Suppress("UNCHECKED_CAST")
  (javaClass.superclass
      .getDeclaredField("permitPackageCycles")
      .apply { isAccessible = true }
      .get(this) as org.gradle.api.provider.Property<Boolean>)
    .set(true)
  sourcePath {
    srcDir(layout.buildDirectory.dir("stagedProtos"))
    include("shared/protos/forms/*.proto")
    include("shared/protos/data/*.proto")
    include("shared/protos/survey/*.proto")
    include("shared/protos/library/*.proto")
    include("shared/protos/userdata/*.proto")
    include("shared/protos/sync/*.proto")
    include("shared/protos/earthengine/*.proto")
  }
  sourcePath {
    srcJar(libs.google.common.protos)
    include("google/type/date.proto")
    include("google/type/timeofday.proto")
  }
  kotlin {
    boxOneOfsMinSize = 5000
    rpcCallStyle = "suspending"
    rpcRole = "server"
  }
  // Field tables for the ProtoJSON and textproto codecs (see ProtoSchemaDescriptorsFactory).
  custom {
    schemaHandlerFactory = ProtoSchemaDescriptorsFactory()
    exclusive = false
    out = layout.buildDirectory.dir("generated/source/protoSchema").get().asFile.path
    options =
      mapOf(
        "roots" to
          listOf(
              "openforis.collectone.v1.forms.FormDef",
              "openforis.collectone.v1.forms.RecordInstance",
              "openforis.collectone.v1.library.LibraryBundle",
              "openforis.collectone.v1.library.OrganizationLibrarySettings",
            )
            .joinToString(","),
        "package" to "org.openforis.collectone.v2.core.proto.schema",
        "name" to "CoreProtoSchema",
      )
  }
}

tasks
  .matching { it.name == "generateCommonMainProtos" }
  .configureEach {
    dependsOn(stageProtos)
    dependsOn(tasks.matching { it.name == "transformCommonMainDependenciesMetadata" })
    // Regenerate the field tables when their generator (in build-logic) changes.
    inputs.files(ProtoSchemaDescriptorsFactory::class.java.protectionDomain.codeSource.location)
  }
