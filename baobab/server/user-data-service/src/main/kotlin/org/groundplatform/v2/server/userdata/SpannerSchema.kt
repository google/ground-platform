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
package org.groundplatform.v2.server.userdata

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
   * proto types, updated proto descriptors, and newly added tables and indexes.
   */
  fun apply(admin: DatabaseAdminClient, settings: SpannerSettings) {
    val existing = admin.getDatabaseDdlResponse(settings.instanceId, settings.databaseId)
    val statementsToRun =
      if (existing.statementsList.none { it.startsWith("CREATE TABLE Surveys") }) {
        statements
      } else {
        upgradeStatements(existing.statementsList, existing.protoDescriptors.toByteArray())
      }
    if (statementsToRun.isEmpty()) return
    val database =
      admin.newDatabaseBuilder(settings.database).setProtoDescriptors(descriptors).build()
    admin.updateDatabaseDdl(database, statementsToRun, null).get()
  }

  private fun upgradeStatements(
    existingStatements: List<String>,
    existingDescriptors: ByteArray,
  ): List<String> = buildList {
    val existingBundle =
      existingStatements.firstOrNull { it.startsWith("CREATE PROTO BUNDLE") }?.let(::bundleTypesIn)
        ?: emptyList()
    val addedTypes = protoBundleTypes - existingBundle.toSet()
    val updatedTypes = existingBundle.intersect(protoBundleTypes.toSet())
    val bundleChanged =
      addedTypes.isNotEmpty() || bundleTypes(existingDescriptors) != bundleTypes(descriptors)
    if (bundleChanged) {
      add(
        buildString {
          append("ALTER PROTO BUNDLE")
          if (addedTypes.isNotEmpty()) append(" INSERT (${addedTypes.joinToString()})")
          if (updatedTypes.isNotEmpty()) append(" UPDATE (${updatedTypes.joinToString()})")
        }
      )
    }
    val existingNames = existingStatements.mapNotNull(::objectName).toSet()
    for (statement in statements) {
      val name = objectName(statement) ?: continue
      if (name !in existingNames) add(statement)
    }
  }

  private fun objectName(statement: String): String? =
    Regex("^CREATE (?:(?:UNIQUE )?(?:NULL_FILTERED )?INDEX|TABLE) (\\w+)")
      .find(statement)
      ?.groupValues
      ?.get(1)

  /**
   * The descriptors of the bundled types in a descriptor set. Spanner returns only the files the
   * bundle needs, so sets are compared by type rather than byte for byte.
   */
  private fun bundleTypes(descriptorSet: ByteArray): Map<String, Message> {
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
