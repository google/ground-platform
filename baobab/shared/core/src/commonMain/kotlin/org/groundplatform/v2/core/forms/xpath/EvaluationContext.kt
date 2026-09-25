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
package org.groundplatform.v2.core.forms.xpath

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.TranslationCatalog
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Immutable evaluation context passed during XPath expression evaluation.
 *
 * Encapsulates the primary instance virtual tree, active context node (`.`), active question node
 * (`current()`), repeat position/size, secondary lookup dataset provider, translations, and
 * deterministic clock/PRNG hooks for testing.
 */
data class EvaluationContext(
  /** Virtual document root node of the primary instance (`/`). */
  val rootNode: XPathNode,
  /** Current context node (`.`). Defaults to the primary instance element node (`/data`). */
  val contextNode: XPathNode =
    if (rootNode is XPathNode.DocumentRootNode) rootNode.instanceElementNode else rootNode,
  /**
   * The node representing the question/binding currently being evaluated. Returned by the XForms
   * `current()` function inside filter predicates (e.g. `instance('cities')/root/item[state =
   * current()/../state]`).
   */
  val currentQuestionNode: XPathNode = contextNode,
  /**
   * 1-based position index of [contextNode] within the active evaluation node-set (`position()`).
   */
  val contextPosition: Int = contextNode.repeatIndex,
  /** Total size of the active evaluation node-set (`last()`). */
  val contextSize: Int = contextNode.siblingRepeatCount,
  /** Provider for secondary instances (`instance('id')` and `pulldata()`). */
  val secondaryInstanceProvider: SecondaryInstanceProvider = SecondaryInstanceProvider.EMPTY,
  /** Optional form definition providing choice labels (`jr:choice-name`) and translations. */
  val formDef: FormDef? = null,
  /** Optional explicit translation catalog (defaults to `formDef.model.translations`). */
  val translations: TranslationCatalog? = formDef?.model?.translations,
  /**
   * Active BCP-47 language tag or language name for `jr:itext()` (defaults to
   * `formDef.default_language`).
   */
  val activeLanguage: String? = formDef?.default_language,
  /** Named variables accessible via `$var` or `${var}`. */
  val variables: Map<String, XPathValue> = emptyMap(),
  /**
   * Supplier for current UTC epoch milliseconds (`today()` and `now()`). Defaults to
   * `1789675200000L` (2026-09-17T20:00:00Z) if unspecified in pure KMP.
   */
  val clockEpochMillis: () -> Long = { DEFAULT_EPOCH_MILLIS },
  /** Optional deterministic seed for `random()` and `uuid()`. */
  val randomSeed: Long? = null,
) {

  /** Creates a child context for evaluating a step or predicate on [newNode]. */
  fun withContextNode(
    newNode: XPathNode,
    position: Int = newNode.repeatIndex,
    size: Int = newNode.siblingRepeatCount,
  ): EvaluationContext = copy(contextNode = newNode, contextPosition = position, contextSize = size)

  companion object {
    // Default epoch millis: 2026-09-17T00:00:00Z (can be overridden by platform clock)
    const val DEFAULT_EPOCH_MILLIS: Long = 1789603200000L

    /**
     * Creates an [EvaluationContext] directly from a [RecordInstance] and optional [FormDef].
     *
     * @param recordInstance The populated form submission record.
     * @param formDef Optional form definition supplying schema, translations, choices, and inline
     *   datasets.
     * @param contextPath Optional slash-separated path to set as the initial context node (`.`).
     * @param secondaryInstanceProvider Optional custom secondary instance provider.
     */
    fun fromRecordInstance(
      recordInstance: RecordInstance,
      formDef: FormDef? = null,
      schema: RecordSchema? = formDef?.model?.primary_instance?.record_schema,
      contextPath: String? = null,
      secondaryInstanceProvider: SecondaryInstanceProvider? = null,
      activeLanguage: String? = formDef?.default_language,
      variables: Map<String, XPathValue> = emptyMap(),
      clockEpochMillis: () -> Long = { DEFAULT_EPOCH_MILLIS },
      randomSeed: Long? = null,
    ): EvaluationContext {
      val docRoot = XPathNode.fromRecordInstance(recordInstance, schema)
      val provider =
        secondaryInstanceProvider
          ?: (if (formDef != null) InMemorySecondaryInstanceProvider.fromFormDef(formDef)
          else SecondaryInstanceProvider.EMPTY)

      val baseContext =
        EvaluationContext(
          rootNode = docRoot,
          contextNode = docRoot.instanceElementNode,
          currentQuestionNode = docRoot.instanceElementNode,
          secondaryInstanceProvider = provider,
          formDef = formDef,
          activeLanguage = activeLanguage,
          variables = variables,
          clockEpochMillis = clockEpochMillis,
          randomSeed = randomSeed,
        )

      if (contextPath.isNullOrBlank()) {
        return baseContext
      }

      // Resolve initial context node from path
      val resolved = XPathEngine.evaluate(contextPath, baseContext)
      val targetNode =
        if (resolved is XPathValue.NodeSet && resolved.nodes.isNotEmpty()) {
          resolved.nodes.first()
        } else {
          docRoot.instanceElementNode
        }
      return baseContext.copy(
        contextNode = targetNode,
        currentQuestionNode = targetNode,
        contextPosition = targetNode.repeatIndex,
        contextSize = targetNode.siblingRepeatCount,
      )
    }
  }
}
