/*
 * Copyright 2026 The Ground Authors.
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

// User data service: serves the Collect One SurveyService, OrganizationService, LibraryService,
// and DataService over native gRPC and gRPC-Web. Run locally with `./gradlew run`; package for
// Cloud Run with `./gradlew installDist` and the Dockerfile in this directory.
plugins {
  kotlin("jvm") version "2.4.20"
  application
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

ktfmt { googleStyle() }

kotlin { jvmToolchain(21) }

dependencies {
  implementation("org.groundplatform.v2:protoforms:2.0.0-SNAPSHOT")
  implementation(platform("com.linecorp.armeria:armeria-bom:1.42.0"))
  implementation("com.linecorp.armeria:armeria")
  implementation("com.linecorp.armeria:armeria-grpc")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  // Reads the proto JSON seed (same Wire version as protoforms).
  implementation("com.squareup.wire:wire-moshi-adapter:7.0.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  // Cloud Spanner (the emulator in development) and the Spanner debug web UI, which records the
  // client's OpenTelemetry spans to show database operations.
  implementation(platform("com.google.cloud:libraries-bom:26.90.0"))
  implementation("com.google.cloud:google-cloud-spanner")
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.67.0"))
  implementation("io.opentelemetry:opentelemetry-sdk")
  runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
  constraints {
    // Armeria's JSON support generates classes with Byte Buddy; older versions fail on JDKs newer
    // than they know about (e.g. when running the installed binary with a recent local JDK).
    implementation("net.bytebuddy:byte-buddy:1.18.15")
  }

  testImplementation(kotlin("test-junit5"))
  // Native gRPC client for end-to-end tests (same grpc-java version Armeria uses).
  testImplementation("io.grpc:grpc-okhttp:1.84.0")
}

application {
  mainClass.set("org.groundplatform.v2.server.userdata.MainKt")
  applicationName = "user-data-service"
}

tasks.test { useJUnitPlatform() }
