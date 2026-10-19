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
package org.openforis.collectone.v2.server.userdata

import com.google.cloud.spanner.DatabaseAdminClient
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Message

/**
 * The user data service's Spanner schema (`src/main/resources/spanner/schema.sql`) and the proto
 * descriptors its `PROTO` columns use (`spanner/descriptors.pb`, generated from `shared/protos` at
 * build time).
 */
object SpannerSchema {
  private const val SCHEMA_RESOURCE = "/spanner/schema.sql"
  private const val DESCRIPTORS_RESOURCE = "/spanner/descriptors.pb"

  /** The schema's DDL statements, in order. */
  val statements: List<String> by lazy {
    resource(SCHEMA_RESOURCE)
      .decodeToString()
      .lines()
      .filterNot { it.trimStart().startsWith("--") }
      .joinToString("\n")
      .split(Regex(";\\s*\n"))
      .map { it.trim().removeSuffix(";").trim() }
      .filter { it.isNotEmpty() }
  }

  /** `FileDescriptorSet` of the protos stored in `PROTO` columns. */
  val descriptors: ByteArray by lazy { resource(DESCRIPTORS_RESOURCE) }

  /** Fully qualified proto types of the schema's `CREATE PROTO BUNDLE` statement. */
  val protoBundleTypes: List<String> by lazy {
    bundleTypesIn(statements.first { it.startsWith("CREATE PROTO BUNDLE") })
  }

  private fun bundleTypesIn(statement: String): List<String> =
    statement.substringAfter("(").substringBeforeLast(")").split(",").map { it.trim() }

  /** Tables the schema creates. */
  val tables: List<String> by lazy {
    statements.mapNotNull { Regex("^CREATE TABLE (\\w+)").find(it)?.groupValues?.get(1) }
  }

  /**
   * Creates the schema in an empty database, or updates an existing database with any new bundled
   * proto types, updated proto descriptors, and newly added tables, columns, and indexes. Changes
   * to existing columns or indexes aren't applied.
   */
  fun apply(admin: DatabaseAdminClient, settings: SpannerSettings) {
    val existing = admin.getDatabaseDdlResponse(settings.instanceId, settings.databaseId)
    val database =
      admin.newDatabaseBuilder(settings.database).setProtoDescriptors(descriptors).build()
    if (existing.statementsList.none { it.startsWith("CREATE TABLE Surveys") }) {
      admin.updateDatabaseDdl(database, statements, null).get()
      return
    }
    val upgrade =
      upgradeStatements(existing.statementsList, existing.protoDescriptors.toByteArray())
    if (upgrade.isEmpty()) return
    if (settings.emulatorHost != null && upgrade.any { it.startsWith("ALTER PROTO BUNDLE") }) {
      // The emulator breaks existing generated columns when the proto bundle changes. Its data is
      // in memory and disposable, so recreate the database; the store then loads the seed.
      System.err.println(
        "Spanner emulator: the proto bundle changed; recreating database " +
          "${settings.databaseId} with the current schema (its data is reset)."
      )
      admin.dropDatabase(settings.instanceId, settings.databaseId)
      admin.createDatabase(settings.instanceId, settings.databaseId, emptyList()).get()
      admin.updateDatabaseDdl(database, statements, null).get()
      return
    }
    admin.updateDatabaseDdl(database, upgrade, null).get()
  }

  private fun upgradeStatements(
    existingStatements: List<String>,
    existingDescriptors: ByteArray,
  ): List<String> = buildList {
    val existingBundle =
      existingStatements.firstOrNull { it.startsWith("CREATE PROTO BUNDLE") }?.let(::bundleTypesIn)
        ?: emptyList()
    val addedTypes = protoBundleTypes - existingBundle.toSet()
    val existingTypeDescriptors = bundleTypes(existingDescriptors)
    val wantedTypeDescriptors = bundleTypes(descriptors)
    val changedTypes = existingBundle.filter {
      it in wantedTypeDescriptors && existingTypeDescriptors[it] != wantedTypeDescriptors[it]
    }
    if (addedTypes.isNotEmpty() || changedTypes.isNotEmpty()) {
      add(
        buildString {
          append("ALTER PROTO BUNDLE")
          if (addedTypes.isNotEmpty()) append(" INSERT (${addedTypes.joinToString()})")
          if (changedTypes.isNotEmpty()) append(" UPDATE (${changedTypes.joinToString()})")
        }
      )
    }
    val existingNames = existingStatements.mapNotNull(::objectName).toSet()
    val existingColumns =
      existingStatements
        .filter { it.startsWith("CREATE TABLE") }
        .associate {
          objectName(it) to columnDefinitions(it).keys
        }
    for (statement in statements) {
      val name = objectName(statement) ?: continue
      if (name !in existingNames) {
        add(statement)
      } else if (statement.startsWith("CREATE TABLE")) {
        val present = existingColumns[name].orEmpty()
        for ((column, definition) in columnDefinitions(statement)) {
          if (column !in present) add("ALTER TABLE $name ADD COLUMN $definition")
        }
      }
    }
  }

  /** Column name → definition of a `CREATE TABLE` statement, one column per line. */
  private fun columnDefinitions(statement: String): Map<String, String> =
    statement
      .substringAfter("(")
      .substringBeforeLast(") PRIMARY KEY")
      .lines()
      .map { it.trim().removeSuffix(",").trim() }
      .filter { it.isNotEmpty() && !it.startsWith("CONSTRAINT") }
      .associateBy { it.substringBefore(" ") }

  private fun objectName(statement: String): String? =
    Regex("^CREATE (?:(?:UNIQUE )?(?:NULL_FILTERED )?(?:SEARCH )?INDEX|TABLE) (\\w+)")
      .find(statement)
      ?.groupValues
      ?.get(1)

  /**
   * The descriptors of the bundled types in a descriptor set. Spanner returns only the files the
   * bundle needs, so sets are compared by type rather than byte for byte.
   */
  private fun bundleTypes(descriptorSet: ByteArray): Map<String, Message> {
    if (descriptorSet.isEmpty()) return emptyMap()
    val types = mutableMapOf<String, Message>()
    fun addMessage(prefix: String, message: DescriptorProto) {
      val name = "$prefix.${message.name}"
      types[name] = message
      message.nestedTypeList.forEach { addMessage(name, it) }
      message.enumTypeList.forEach { types["$name.${it.name}"] = it }
    }
    for (file in FileDescriptorSet.parseFrom(descriptorSet).fileList) {
      file.messageTypeList.forEach { addMessage(file.`package`, it) }
      file.enumTypeList.forEach { types["${file.`package`}.${it.name}"] = it }
    }
    return protoBundleTypes.mapNotNull { type -> types[type]?.let { type to it } }.toMap()
  }

  private fun resource(name: String): ByteArray =
    checkNotNull(SpannerSchema::class.java.getResourceAsStream(name)) {
        "Missing classpath resource $name"
      }
      .use { it.readBytes() }
}
