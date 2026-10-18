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
    val bundle = statements.first { it.startsWith("CREATE PROTO BUNDLE") }
    bundle.substringAfter("(").substringBeforeLast(")").split(",").map { it.trim() }
  }

  /** Tables the schema creates. */
  val tables: List<String> by lazy {
    statements.mapNotNull { Regex("^CREATE TABLE (\\w+)").find(it)?.groupValues?.get(1) }
  }

  /**
   * Creates the schema in an empty database, or refreshes its proto bundle (so `PROTO` columns know
   * fields added to the protos since) when the schema already exists.
   */
  fun apply(admin: DatabaseAdminClient, settings: SpannerSettings) {
    val existing = admin.getDatabaseDdlResponse(settings.instanceId, settings.databaseId)
    val statementsToRun =
      when {
        existing.statementsList.none { it.startsWith("CREATE TABLE Surveys") } -> statements
        bundleTypes(existing.protoDescriptors.toByteArray()) == bundleTypes(descriptors) -> return
        else -> listOf("ALTER PROTO BUNDLE UPDATE (${protoBundleTypes.joinToString()})")
      }
    val database =
      admin.newDatabaseBuilder(settings.database).setProtoDescriptors(descriptors).build()
    admin.updateDatabaseDdl(database, statementsToRun, null).get()
  }

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
