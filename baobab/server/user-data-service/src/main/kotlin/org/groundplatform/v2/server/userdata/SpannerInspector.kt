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
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.ResultSet
import com.google.cloud.spanner.Statement
import com.google.cloud.spanner.Struct
import com.google.cloud.spanner.Type
import com.squareup.wire.ProtoAdapter
import java.util.Base64

/** A column of a table or query result. */
data class SpannerColumn(
  val name: String,
  val type: String,
  val nullable: Boolean = true,
  /** Generation expression of a generated column. */
  val generatedAs: String? = null,
  val stored: Boolean = false,
) {
  fun toMap(): Map<String, Any?> =
    mapOf(
      "name" to name,
      "type" to type,
      "nullable" to nullable,
      "generatedAs" to generatedAs,
      "stored" to stored,
    )
}

/** A secondary or search index. */
data class SpannerIndex(
  val name: String,
  val type: String,
  val unique: Boolean,
  val nullFiltered: Boolean,
  /** Key columns with their ordering, e.g. `CommitTs ASC`. */
  val keyColumns: List<String>,
  val storing: List<String>,
  /** Table the index is interleaved in, if any. */
  val interleavedIn: String?,
) {
  fun toMap(): Map<String, Any?> =
    mapOf(
      "name" to name,
      "type" to type,
      "unique" to unique,
      "nullFiltered" to nullFiltered,
      "keyColumns" to keyColumns,
      "storing" to storing,
      "interleavedIn" to interleavedIn,
    )
}

/** Schema of one table. */
data class SpannerTable(
  val name: String,
  /** Interleaving parent, if any. */
  val parent: String?,
  val onDelete: String?,
  val rowDeletionPolicy: String?,
  val columns: List<SpannerColumn>,
  /** Primary key columns with their ordering, e.g. `RevisionNumber DESC`. */
  val primaryKey: List<String>,
  val indexes: List<SpannerIndex>,
) {
  fun toMap(): Map<String, Any?> =
    mapOf(
      "name" to name,
      "parent" to parent,
      "onDelete" to onDelete,
      "rowDeletionPolicy" to rowDeletionPolicy,
      "columns" to columns.map { it.toMap() },
      "primaryKey" to primaryKey,
      "indexes" to indexes.map { it.toMap() },
    )
}

/** Rows of a table page or a query. Each cell is a JSON-friendly value (see [SpannerValues]). */
data class SpannerRows(
  val columns: List<SpannerColumn>,
  val rows: List<List<Any?>>,
  /** True when more rows exist after these. */
  val hasMore: Boolean,
) {
  fun toMap(): Map<String, Any?> =
    mapOf("columns" to columns.map { it.toMap() }, "rows" to rows, "hasMore" to hasMore)
}

/**
 * Reads schema and data for the Spanner debug UI.
 *
 * Table browsing uses [inspectorClient], which isn't recorded. SQL typed into the UI runs on
 * [queryClient] (the service's recorded client), in a single-use read-only transaction, so it can't
 * change data and shows up in the operation log.
 */
class SpannerInspector(
  private val inspectorClient: DatabaseClient,
  private val queryClient: DatabaseClient,
  private val databaseAdmin: DatabaseAdminClient,
  private val settings: SpannerSettings,
) {
  /** Every table in the default schema, with columns, keys, and indexes, sorted by name. */
  fun tables(): List<SpannerTable> {
    val tables = query("SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ''")
    val columns =
      query("SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ''").groupBy {
        it.string("TABLE_NAME")
      }
    val indexes =
      query("SELECT * FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_SCHEMA = ''").groupBy {
        it.string("TABLE_NAME")
      }
    val indexColumns =
      query("SELECT * FROM INFORMATION_SCHEMA.INDEX_COLUMNS WHERE TABLE_SCHEMA = ''").groupBy {
        it.string("TABLE_NAME") to it.string("INDEX_NAME")
      }
    fun keyOf(table: String, index: String): Pair<List<String>, List<String>> {
      val all = indexColumns[table to index].orEmpty()
      val keys =
        all
          .filter { it.long("ORDINAL_POSITION") != null }
          .sortedBy { it.long("ORDINAL_POSITION") }
          .map { c ->
            listOfNotNull(c.string("COLUMN_NAME"), c.string("COLUMN_ORDERING")).joinToString(" ")
          }
      val storing =
        all.filter { it.long("ORDINAL_POSITION") == null }.mapNotNull { it.string("COLUMN_NAME") }
      return keys to storing
    }
    return tables
      .filter { (it.string("TABLE_TYPE") ?: "BASE TABLE") == "BASE TABLE" }
      .mapNotNull { t ->
        val name = t.string("TABLE_NAME") ?: return@mapNotNull null
        SpannerTable(
          name = name,
          parent = t.string("PARENT_TABLE_NAME")?.takeIf { it.isNotBlank() },
          onDelete = t.string("ON_DELETE_ACTION"),
          rowDeletionPolicy = t.string("ROW_DELETION_POLICY_EXPRESSION"),
          columns =
            columns[name]
              .orEmpty()
              .sortedBy { it.long("ORDINAL_POSITION") }
              .map { c ->
                SpannerColumn(
                  name = c.string("COLUMN_NAME").orEmpty(),
                  type = c.string("SPANNER_TYPE").orEmpty(),
                  nullable = c.string("IS_NULLABLE") != "NO",
                  generatedAs = c.string("GENERATION_EXPRESSION"),
                  stored = c.string("IS_STORED") == "YES",
                )
              },
          primaryKey = keyOf(name, "PRIMARY_KEY").first,
          indexes =
            indexes[name]
              .orEmpty()
              .filter { it.string("INDEX_TYPE") != "PRIMARY_KEY" }
              .map { i ->
                val indexName = i.string("INDEX_NAME").orEmpty()
                val (keys, storing) = keyOf(name, indexName)
                SpannerIndex(
                  name = indexName,
                  type = i.string("INDEX_TYPE").orEmpty(),
                  unique = i.bool("IS_UNIQUE") == true,
                  nullFiltered = i.bool("IS_NULL_FILTERED") == true,
                  keyColumns = keys,
                  storing = storing,
                  interleavedIn = i.string("PARENT_TABLE_NAME")?.takeIf { it.isNotBlank() },
                )
              },
        )
      }
      .sortedBy { it.name }
  }

  /** Number of rows in [table]. */
  fun rowCount(table: String): Long {
    val name = requireTable(table).name
    inspectorClient
      .singleUse()
      .executeQuery(Statement.of("SELECT COUNT(*) FROM ${quote(name)}"))
      .use { rs ->
        rs.next()
        return rs.getLong(0)
      }
  }

  /** A page of [table]'s rows in primary key order. */
  fun rows(table: String, limit: Int, offset: Int): SpannerRows {
    val schema = requireTable(table)
    val orderBy =
      schema.primaryKey
        .takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = " ORDER BY ") { key ->
          val parts = key.split(" ")
          quote(parts[0]) + (parts.getOrNull(1)?.let { " $it" } ?: "")
        }
        .orEmpty()
    val pageSize = limit.coerceIn(1, MAX_ROWS)
    val statement =
      Statement.newBuilder(
          "SELECT * FROM ${quote(schema.name)}$orderBy LIMIT @limit OFFSET @offset"
        )
        .bind("limit")
        .to(pageSize + 1L)
        .bind("offset")
        .to(offset.coerceAtLeast(0).toLong())
        .build()
    return inspectorClient.singleUse().executeQuery(statement).use { readRows(it, pageSize) }
  }

  /** Runs read-only [sql] (recorded as an operation) and returns up to [limit] rows. */
  fun runQuery(sql: String, limit: Int = MAX_ROWS): SpannerRows {
    require(sql.isNotBlank()) { "Enter a SQL query" }
    return queryClient.singleUse().executeQuery(Statement.of(sql)).use {
      readRows(it, limit.coerceIn(1, MAX_ROWS))
    }
  }

  /** The database's DDL statements. */
  fun ddl(): List<String> = databaseAdmin.getDatabaseDdl(settings.instanceId, settings.databaseId)

  private fun requireTable(table: String): SpannerTable =
    requireNotNull(tables().firstOrNull { it.name == table }) { "Unknown table: $table" }

  private fun readRows(rs: ResultSet, limit: Int): SpannerRows {
    val rows = mutableListOf<List<Any?>>()
    var hasMore = false
    while (rs.next()) {
      if (rows.size == limit) {
        hasMore = true
        break
      }
      val row = rs.currentRowAsStruct
      rows += (0 until row.columnCount).map { SpannerValues.render(row, it) }
    }
    val columns =
      rs.type.structFields.map { SpannerColumn(it.name, SpannerValues.typeName(it.type)) }
    return SpannerRows(columns, rows, hasMore)
  }

  private fun query(sql: String): List<Struct> =
    inspectorClient.singleUse().executeQuery(Statement.of(sql)).use { rs ->
      buildList { while (rs.next()) add(rs.currentRowAsStruct) }
    }

  companion object {
    /** Largest number of rows returned by one page or query. */
    const val MAX_ROWS = 500

    private fun quote(identifier: String) = "`" + identifier.replace("`", "") + "`"

    private fun Struct.has(column: String) = type.structFields.any { it.name == column }

    private fun Struct.string(column: String): String? =
      if (has(column) && !isNull(column)) getString(column) else null

    private fun Struct.long(column: String): Long? =
      if (has(column) && !isNull(column)) getLong(column) else null

    private fun Struct.bool(column: String): Boolean? =
      if (!has(column) || isNull(column)) null
      else if (getColumnType(column).code == Type.Code.BOOL) getBoolean(column)
      else getString(column) == "YES"
  }
}

/**
 * Converts Spanner values into JSON-friendly cells for the debug UI. Scalars become strings,
 * numbers, or booleans. `PROTO` columns are decoded with the Wire-generated class for their message
 * type and returned as `{"kind": "proto", "type", "size", "json"}`; `BYTES` become `{"kind":
 * "bytes", "size", "base64"}`; `ENUM` values become their name.
 */
object SpannerValues {
  /** Largest number of bytes returned for a `BYTES` value. */
  const val MAX_BYTES = 4096

  private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L

  fun render(row: Struct, index: Int): Any? {
    if (row.isNull(index)) return null
    val type = row.getColumnType(index)
    return when (type.code) {
      Type.Code.BOOL -> row.getBoolean(index)
      Type.Code.INT64 ->
        row.getLong(index).let {
          if (it in -MAX_SAFE_JSON_INTEGER..MAX_SAFE_JSON_INTEGER) it else it.toString()
        }
      Type.Code.FLOAT64 -> row.getDouble(index).let { if (it.isFinite()) it else it.toString() }
      Type.Code.STRING -> row.getString(index)
      Type.Code.BYTES -> bytes(row.getBytes(index).toByteArray())
      Type.Code.PROTO -> proto(type.protoTypeFqn, row.getBytes(index).toByteArray())
      Type.Code.ENUM -> enumName(type.protoTypeFqn, row.getLong(index))
      Type.Code.JSON -> row.getJson(index)
      Type.Code.NUMERIC -> row.getBigDecimal(index).toPlainString()
      Type.Code.TIMESTAMP -> row.getTimestamp(index).toString()
      Type.Code.DATE -> row.getDate(index).toString()
      // `Value.toString()` abbreviates long values, so convert them in full.
      Type.Code.ARRAY -> row.getValue(index).getAsStringList()
      else -> row.getValue(index).getAsString()
    }
  }

  /** SQL-style name of [type], e.g. `STRING`, `ARRAY<INT64>`, or a proto type's full name. */
  fun typeName(type: Type): String =
    when (type.code) {
      Type.Code.ARRAY -> "ARRAY<${typeName(type.arrayElementType)}>"
      Type.Code.PROTO,
      Type.Code.ENUM -> type.protoTypeFqn
      else -> type.code.name
    }

  private fun bytes(value: ByteArray): Map<String, Any?> =
    mapOf(
      "kind" to "bytes",
      "size" to value.size,
      "base64" to Base64.getEncoder().encodeToString(value.copyOf(minOf(value.size, MAX_BYTES))),
      "truncated" to (value.size > MAX_BYTES),
    )

  private fun proto(typeName: String, value: ByteArray): Map<String, Any?> {
    val json = runCatching {
      val message = adapterFor(typeName).decode(value)
      protoJsonMoshi.adapter<Any>(message.javaClass).indent("  ").toJson(message)
    }
      .getOrNull()
    return if (json != null) {
      mapOf("kind" to "proto", "type" to typeName, "size" to value.size, "json" to json)
    } else {
      bytes(value) + mapOf("kind" to "proto", "type" to typeName, "decodeError" to true)
    }
  }

  private fun enumName(typeName: String, number: Long): String =
    runCatching {
      val value =
        classFor(typeName).getMethod("fromValue", Int::class.java).invoke(null, number.toInt())
      (value as? Enum<*>)?.name
    }
      .getOrNull() ?: number.toString()

  @Suppress("UNCHECKED_CAST")
  private fun adapterFor(typeName: String): ProtoAdapter<Any> =
    ProtoAdapter.get(classFor(typeName)) as ProtoAdapter<Any>

  /**
   * The Wire-generated class for a fully qualified proto type name. Wire uses the proto package as
   * the Kotlin package and nests inner types, so `a.b.Outer.Inner` is the class `a.b.Outer$Inner`.
   */
  private fun classFor(typeName: String): Class<*> {
    var candidate = typeName
    while (true) {
      runCatching { Class.forName(candidate) }
        .onSuccess {
          return it
        }
      val dot = candidate.lastIndexOf('.')
      require(dot > 0) { "No generated class for $typeName" }
      candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1)
    }
  }
}
