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
package org.openforis.collectone.v2.core.forms.serialization

import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibrarySettings
import org.openforis.collectone.v2.core.library.LibraryTextProtoSerializer
import org.openforis.collectone.v2.core.proto.FullyPopulatedProtos

/**
 * Pins the exact output of the ProtoJSON and textproto codecs, byte for byte.
 *
 * Each case writes a message (or parses an input) and compares the result with a golden file under
 * `src/jvmTest/resources/serialization-goldens/`. Encoding cases cover every field (the
 * [FullyPopulatedProtos] variants), the edge cases in [SerializationGoldenFixtures], and the global
 * library seeds; decoding cases pin how lenient inputs are read (the [toString] of the decoded
 * message, or the exception type).
 *
 * A diff here means the wire format changed. The goldens only change on purpose: run the test with
 * the environment variable `UPDATE_SERIALIZATION_GOLDENS=true` and review the diff of the goldens.
 */
class SerializationGoldenTest {

  @Test
  fun outputMatchesGoldens() {
    val update = System.getenv("UPDATE_SERIALIZATION_GOLDENS") == "true"
    val mismatches = mutableListOf<String>()
    val expectedFiles = mutableSetOf<String>()
    for ((name, produce) in cases()) {
      expectedFiles += name
      val actual = runCatching(produce).getOrElse { "THROWS ${it::class.qualifiedName}\n" }
      val golden = File(GOLDEN_DIR, name)
      if (update) {
        golden.parentFile.mkdirs()
        golden.writeText(actual)
      } else if (!golden.isFile) {
        mismatches += "$name: missing golden"
      } else if (golden.readText() != actual) {
        mismatches +=
          "$name: differs from golden\n--- golden\n${golden.readText()}\n+++ actual\n$actual"
      }
    }
    val stale = File(GOLDEN_DIR).list().orEmpty().filter { it !in expectedFiles }
    if (update) stale.forEach { File(GOLDEN_DIR, it).delete() }
    else stale.forEach { mismatches += "$it: golden without a case" }
    if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n\n"))
  }

  private fun cases(): List<Pair<String, () -> String>> = buildList {
    addEncodingCases()
    addDecodingCases()
  }

  private fun MutableList<Pair<String, () -> String>>.addEncodingCases() {
    val formDefs =
      variants(FormDef::class.java).mapIndexed { i, form -> "form_full_$i" to form } +
        SerializationGoldenFixtures.formDefs.toList()
    for ((name, formDef) in formDefs) {
      add("$name.json" to { ProtoJsonSerializer.serializeFormDef(formDef) })
      add(
        "$name.snake.json" to
          {
            ProtoJsonSerializer.serializeFormDef(formDef, preserveProtoFieldNames = true)
          }
      )
      add(
        "$name.compact.json" to
          {
            ProtoJsonSerializer.serializeFormDef(formDef, prettyPrint = false)
          }
      )
      add("$name.textproto" to { TextProtoSerializer.serializeFormDef(formDef) })
    }

    val records =
      variants(RecordInstance::class.java).mapIndexed { i, record -> "record_full_$i" to record } +
        SerializationGoldenFixtures.records.toList()
    for ((name, record) in records) {
      add("$name.json" to { ProtoJsonSerializer.serializeRecordInstance(record) })
      add(
        "$name.snake.json" to
          {
            ProtoJsonSerializer.serializeRecordInstance(record, preserveProtoFieldNames = true)
          }
      )
      add(
        "$name.compact.json" to
          {
            ProtoJsonSerializer.serializeRecordInstance(record, prettyPrint = false)
          }
      )
      add("$name.textproto" to { TextProtoSerializer.serializeRecordInstance(record) })
    }

    val bundles =
      variants(LibraryBundle::class.java).mapIndexed { i, bundle -> "library_full_$i" to bundle } +
        SerializationGoldenFixtures.bundles.toList()
    for ((name, bundle) in bundles) {
      add("$name.textproto" to { LibraryTextProtoSerializer.serializeBundle(bundle) })
    }
    variants(OrganizationLibrarySettings::class.java).forEachIndexed { i, settings ->
      add(
        "settings_full_$i.textproto" to { LibraryTextProtoSerializer.serializeSettings(settings) }
      )
    }
    for ((name, settings) in SerializationGoldenFixtures.settings) {
      add("$name.textproto" to { LibraryTextProtoSerializer.serializeSettings(settings) })
    }

    File(SEED_DIR)
      .listFiles { f -> f.extension == "textproto" }!!
      .sorted()
      .forEach { seed ->
        add(
          "seed_${seed.nameWithoutExtension}.textproto" to
            {
              LibraryTextProtoSerializer.serializeBundle(
                LibraryTextProtoSerializer.deserializeBundle(seed.readText())
              )
            }
        )
      }
  }

  private fun MutableList<Pair<String, () -> String>>.addDecodingCases() {
    for ((name, json) in SerializationGoldenFixtures.formDefJsonInputs) {
      add("decode_$name.formdef.txt" to { "${ProtoJsonSerializer.deserializeFormDef(json)}\n" })
    }
    for ((name, json) in SerializationGoldenFixtures.recordJsonInputs) {
      add(
        "decode_$name.record.txt" to
          {
            "${ProtoJsonSerializer.deserializeRecordInstance(json)}\n"
          }
      )
      add(
        "decode_$name.record_with_form.txt" to
          {
            val formDef = SerializationGoldenFixtures.schemaFormDef
            "${ProtoJsonSerializer.deserializeRecordInstance(json, formDef)}\n"
          }
      )
    }
    for ((name, text) in SerializationGoldenFixtures.formDefTextInputs) {
      add("decode_$name.formdef.txt" to { "${TextProtoSerializer.deserializeFormDef(text)}\n" })
    }
    for ((name, text) in SerializationGoldenFixtures.recordTextInputs) {
      add(
        "decode_$name.record.txt" to { "${TextProtoSerializer.deserializeRecordInstance(text)}\n" }
      )
    }
    for ((name, text) in SerializationGoldenFixtures.bundleTextInputs) {
      add(
        "decode_$name.bundle.txt" to { "${LibraryTextProtoSerializer.deserializeBundle(text)}\n" }
      )
    }
    for ((name, text) in SerializationGoldenFixtures.settingsTextInputs) {
      add(
        "decode_$name.settings.txt" to
          {
            "${LibraryTextProtoSerializer.deserializeSettings(text)}\n"
          }
      )
    }
  }

  private fun <M : com.squareup.wire.Message<M, *>> variants(type: Class<M>): List<M> =
    (0 until FullyPopulatedProtos.variantCount(type)).map {
      FullyPopulatedProtos.populate(type, it)
    }

  private companion object {
    const val GOLDEN_DIR = "src/jvmTest/resources/serialization-goldens"
    const val SEED_DIR = "../assets/library"
  }
}
