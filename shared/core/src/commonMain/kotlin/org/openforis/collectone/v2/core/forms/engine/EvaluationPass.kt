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
package org.openforis.collectone.v2.core.forms.engine

import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.model.PlatformEffectRequest
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.SecondaryInstanceProvider
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode

/**
 * The values that stay fixed while one [FormEngine] call evaluates a form: the compiled rules, the
 * language labels resolve in, the host environment and the secondary instances expressions read.
 *
 * It also collects the [pendingRequests] (platform effects such as `odk:setgeopoint`) that actions
 * queue during the call, so every step appends to the same list.
 */
internal class EvaluationPass(
  val compiled: CompiledForm,
  val activeLanguage: String,
  val environment: FormEnvironment,
  initialPendingRequests: List<PlatformEffectRequest> = emptyList(),
) {
  /** Host-provided secondary instances, falling back to the ones inlined in the form. */
  val secondaryProvider: SecondaryInstanceProvider = resolveSecondaryProvider(compiled, environment)

  val pendingRequests: MutableList<PlatformEffectRequest> = initialPendingRequests.toMutableList()

  /** Canonical path of the primary instance root, e.g. `/data`. */
  val rootCanonicalPath: String = "/${compiled.rootName}"

  /**
   * Builds an XPath context over [recordInstance], positioned on [contextCanonicalPath] when given
   * and on the document root otherwise.
   */
  fun evaluationContext(
    recordInstance: RecordInstance,
    contextCanonicalPath: String? = null,
  ): EvaluationContext {
    val base =
      EvaluationContext.fromRecordInstance(
        recordInstance = recordInstance,
        formDef = compiled.formDef,
        schema = compiled.formDef.model?.primary_instance?.record_schema,
        secondaryInstanceProvider = secondaryProvider,
        activeLanguage = activeLanguage,
        variables = environment.variables,
        clockEpochMillis = environment.clockEpochMillis,
        utcOffsetMillis = environment.utcOffsetMillis,
        randomSeed = environment.randomSeed,
      )
    return if (contextCanonicalPath.isNullOrBlank()) {
      base
    } else {
      positionContextAtPath(base, contextCanonicalPath)
    }
  }

  /** Canonical paths of every node in [rootNode] matching the schema [relativePath]. */
  fun concretePaths(rootNode: RecordNode, relativePath: String): List<String> =
    expandConcretePathsForRelativePath(
      rootNode = rootNode,
      rootName = compiled.rootName,
      relativePath = relativePath,
    )
}

private fun resolveSecondaryProvider(
  compiled: CompiledForm,
  environment: FormEnvironment,
): SecondaryInstanceProvider {
  val envProvider =
    environment.secondaryInstanceProvider ?: return compiled.inlineSecondaryInstanceProvider
  val inlineProvider = compiled.inlineSecondaryInstanceProvider
  return object : SecondaryInstanceProvider {
    override fun lookupByEquality(
      instanceId: String,
      keyField: String,
      keyValue: String,
    ): List<Map<String, TypedValue>>? =
      envProvider.lookupByEquality(instanceId, keyField, keyValue)
        ?: inlineProvider.lookupByEquality(instanceId, keyField, keyValue)

    override fun resolveRoot(instanceId: String): XPathNode? =
      envProvider.resolveRoot(instanceId) ?: inlineProvider.resolveRoot(instanceId)
  }
}
