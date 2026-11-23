/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.engine

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.xpath.XPathException
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/**
 * Stateless evaluation engine for ProtoForms and XForms specifications.
 *
 * Evaluates dynamic form states (`relevant`, `calculate`, `required`, `constraint`, `jr:count`
 * repeats, cascading `itemset` queries, localized `<output>` interpolations, preloads, lifecycle
 * events/actions, and XForms Entities) in pure Kotlin Multiplatform.
 */
object FormEngine {

  /** Pre-compiles a [FormDef] into a [CompiledForm] with cached ASTs and dependency ordering. */
  fun compile(formDef: FormDef): CompiledForm = CompiledForm(formDef)

  /**
   * Checks whether [formDef] can be compiled, returning any problems as data instead of throwing.
   *
   * [compile] deliberately fails fast: a form whose bindings form a dependency cycle has no valid
   * evaluation order, and filling it in would silently produce values that depend on declaration
   * order. That is the right behavior for the runtime, but it makes the form impossible to even
   * open, which is a poor experience for whoever has to *fix* it. This entry point exists so form
   * authoring and import tooling can detect the problem, name the fields responsible, and decide
   * its own policy.
   */
  fun validate(formDef: FormDef): FormValidationResult {
    return try {
      compile(formDef)
      FormValidationResult.VALID
    } catch (e: CyclicDependencyException) {
      FormValidationResult(listOf(FormValidationProblem.CircularDependency(e.paths)))
    } catch (e: XPathException) {
      // Expression parsing happens eagerly while indexing bindings, so a malformed calculate or
      // relevant expression also surfaces here rather than at evaluation time.
      FormValidationResult(
        listOf(FormValidationProblem.InvalidExpression(e.message ?: "Invalid XPath expression"))
      )
    }
  }

  /**
   * Initializes a new or reopened form session, executing schema defaults, preload calculations,
   * `EVENT_INSTANCE_FIRST_LOAD` (when [isFirstLoad] is true), and `EVENT_INSTANCE_LOAD` actions
   * before evaluating the full [FormState].
   */
  fun initialize(
    formDef: FormDef,
    existingRecord: RecordInstance? = null,
    isFirstLoad: Boolean = existingRecord == null,
    activeLanguage: String? = null,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState =
    initialize(compile(formDef), existingRecord, isFirstLoad, activeLanguage, environment)

  /** [initialize] with an already [compiled] form, so a session parses its XPath only once. */
  internal fun initialize(
    compiled: CompiledForm,
    existingRecord: RecordInstance?,
    isFirstLoad: Boolean,
    activeLanguage: String?,
    environment: FormEnvironment,
  ): FormState {
    val formDef = compiled.formDef
    val schema = formDef.model?.primary_instance?.record_schema
    val defaultValues = formDef.model?.primary_instance?.default_values

    var rawNode =
      RecordMutator.buildInitialRecordNode(
        schema = schema,
        defaultValues = defaultValues,
        existingNode = existingRecord?.data_,
      )
    var metadata = existingRecord?.metadata ?: formDef.model?.metadata ?: RecordMetadata()
    val pass = EvaluationPass(compiled, resolveActiveLanguage(formDef, activeLanguage), environment)

    // 1. Run preloads for initialization
    val preloaded =
      PreloadExecutor(pass)
        .execute(
          rawNode = rawNode,
          metadata = metadata,
          isFirstLoad = isFirstLoad,
          isFinalize = false,
        )
    rawNode = preloaded.first
    metadata = preloaded.second

    // 2. Dispatch EVENT_INSTANCE_FIRST_LOAD and EVENT_INSTANCE_LOAD actions
    val startupEvents = buildList {
      if (isFirstLoad) add(EventType.EVENT_INSTANCE_FIRST_LOAD)
      add(EventType.EVENT_INSTANCE_LOAD)
    }
    val modelActions =
      compiled.startupActions.filter { action -> action.events.any { it in startupEvents } }
    if (modelActions.isNotEmpty()) {
      val afterActions =
        ActionExecutor(pass)
          .execute(
            actions = modelActions,
            rawNode = rawNode,
            metadata = metadata,
            contextCanonicalPath = pass.rootCanonicalPath,
          )
      rawNode = afterActions.node
      metadata = afterActions.metadata ?: metadata
    }

    val initialRecord =
      RecordInstance(
        form_id = existingRecord?.form_id?.takeIf { it.isNotEmpty() } ?: formDef.form_id,
        form_version = existingRecord?.form_version?.takeIf { it.isNotEmpty() } ?: formDef.version,
        metadata = metadata,
        data_ = rawNode,
        audit_log = existingRecord?.audit_log,
      )

    return evaluateInternal(pass = pass, baseRecord = initialRecord, rawRecordNode = rawNode)
  }

  /**
   * Purely evaluates the complete [FormState] for a given [FormDef] and [RecordInstance] snapshot.
   */
  fun evaluate(
    formDef: FormDef,
    recordInstance: RecordInstance,
    activeLanguage: String? = null,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState {
    val pass =
      EvaluationPass(compile(formDef), resolveActiveLanguage(formDef, activeLanguage), environment)
    val rawNode = recordInstance.data_ ?: RecordNode()
    return evaluateInternal(pass = pass, baseRecord = recordInstance, rawRecordNode = rawNode)
  }

  /**
   * Updates the value of the field at [canonicalPath], triggers any bound `EVENT_VALUE_CHANGED`
   * actions, and recalculates the resulting [FormState].
   */
  fun updateFieldValue(
    state: FormState,
    canonicalPath: String,
    newValue: FieldValue?,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState =
    updateFieldValue(compile(state.formDef), state, canonicalPath, newValue, environment)

  /** [updateFieldValue] reusing [compiled], which must have been compiled from `state.formDef`. */
  internal fun updateFieldValue(
    compiled: CompiledForm,
    state: FormState,
    canonicalPath: String,
    newValue: FieldValue?,
    environment: FormEnvironment,
  ): FormState {
    requireCompiledFrom(compiled, state)
    val resolvedPath =
      RecordMutator.resolveCanonicalPath(
        rawPath = canonicalPath,
        rootName = compiled.rootName,
        rootAliases = compiled.rootAliases,
      )
    val relPath = RecordMutator.normalizeSchemaPath(resolvedPath, compiled.rootAliases)

    // Coerce scalar value to target DataType if needed
    val targetType = compiled.resolveDataType(relPath)
    val coercedValue = coerceFieldValueToType(newValue, targetType)

    var (updatedRawNode, updatedMetadata) =
      RecordMutator.setFieldValue(
        rootNode = state.rawRecordNode,
        metadata = state.recordInstance.metadata,
        rootAliases = compiled.rootAliases,
        canonicalPath = resolvedPath,
        newValue = coercedValue,
      )

    val pass = EvaluationPass(compiled, state.activeLanguage, environment)

    // Trigger EVENT_VALUE_CHANGED actions bound to this control/field
    val watchedActions = compiled.valueChangedActionsBySourcePath[relPath] ?: emptyList()
    if (watchedActions.isNotEmpty()) {
      val parentContextPath = resolvedPath.substringBeforeLast('/', "/${compiled.rootName}")
      val afterActions =
        ActionExecutor(pass)
          .execute(
            actions = watchedActions,
            rawNode = updatedRawNode,
            metadata = updatedMetadata,
            contextCanonicalPath = parentContextPath,
          )
      updatedRawNode = afterActions.node
      updatedMetadata = afterActions.metadata
    }

    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)

    return evaluateInternal(pass = pass, baseRecord = updatedRecord, rawRecordNode = updatedRawNode)
  }

  /**
   * Appends a new repeat instance to the repeat group at [repeatPath], initializes its schema
   * defaults, fires `EVENT_REPEAT_INSERT` (`odk-new-repeat`) actions, and recalculates [FormState].
   */
  fun addRepeatInstance(
    state: FormState,
    repeatPath: String,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState = addRepeatInstance(compile(state.formDef), state, repeatPath, environment)

  /** [addRepeatInstance] reusing [compiled], which must have been compiled from `state.formDef`. */
  internal fun addRepeatInstance(
    compiled: CompiledForm,
    state: FormState,
    repeatPath: String,
    environment: FormEnvironment,
  ): FormState {
    requireCompiledFrom(compiled, state)
    val resolvedRepeatPath =
      RecordMutator.resolveCanonicalPath(
        rawPath = repeatPath,
        rootName = compiled.rootName,
        rootAliases = compiled.rootAliases,
      )
    val relRepeatPath = RecordMutator.normalizeSchemaPath(resolvedRepeatPath, compiled.rootAliases)
    val repeatSchema = compiled.schemaByRelativePath[relRepeatPath]

    val existingInstances =
      RecordMutator.getRepeatInstances(
        rootNode = state.rawRecordNode,
        rootAliases = compiled.rootAliases,
        repeatCanonicalPath = resolvedRepeatPath,
      )
    val newInstanceNode = RecordMutator.buildDefaultRepeatItemNode(repeatSchema)
    val newIndex1Based = existingInstances.size + 1
    val updatedList = existingInstances + newInstanceNode

    var updatedRawNode =
      RecordMutator.setRepeatInstances(
        rootNode = state.rawRecordNode,
        rootAliases = compiled.rootAliases,
        repeatCanonicalPath = resolvedRepeatPath,
        instances = updatedList,
      )
    var updatedMetadata = state.recordInstance.metadata
    val pass = EvaluationPass(compiled, state.activeLanguage, environment)

    // Trigger EVENT_REPEAT_INSERT actions scoped to the newly added repeat instance
    val repeatActions = compiled.repeatInsertActionsByRepeatPath[relRepeatPath] ?: emptyList()
    if (repeatActions.isNotEmpty()) {
      val instanceCanonicalPath = "$resolvedRepeatPath[$newIndex1Based]"
      val afterActions =
        ActionExecutor(pass)
          .execute(
            actions = repeatActions,
            rawNode = updatedRawNode,
            metadata = updatedMetadata,
            contextCanonicalPath = instanceCanonicalPath,
          )
      updatedRawNode = afterActions.node
      updatedMetadata = afterActions.metadata
    }

    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)

    return evaluateInternal(pass = pass, baseRecord = updatedRecord, rawRecordNode = updatedRawNode)
  }

  /**
   * Removes the 1-based [repeatIndex] instance from the repeat group at [repeatPath] and
   * recalculates [FormState].
   */
  fun removeRepeatInstance(
    state: FormState,
    repeatPath: String,
    repeatIndex: Int,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState =
    removeRepeatInstance(compile(state.formDef), state, repeatPath, repeatIndex, environment)

  /**
   * [removeRepeatInstance] reusing [compiled], which must have been compiled from `state.formDef`.
   */
  internal fun removeRepeatInstance(
    compiled: CompiledForm,
    state: FormState,
    repeatPath: String,
    repeatIndex: Int,
    environment: FormEnvironment,
  ): FormState {
    requireCompiledFrom(compiled, state)
    val resolvedRepeatPath =
      RecordMutator.resolveCanonicalPath(
        rawPath = repeatPath,
        rootName = compiled.rootName,
        rootAliases = compiled.rootAliases,
      )
    val existingInstances =
      RecordMutator.getRepeatInstances(
        rootNode = state.rawRecordNode,
        rootAliases = compiled.rootAliases,
        repeatCanonicalPath = resolvedRepeatPath,
      )
    val idx0 = repeatIndex - 1
    if (idx0 !in existingInstances.indices) {
      return state
    }
    val updatedList = existingInstances.toMutableList().apply { removeAt(idx0) }
    val updatedRawNode =
      RecordMutator.setRepeatInstances(
        rootNode = state.rawRecordNode,
        rootAliases = compiled.rootAliases,
        repeatCanonicalPath = resolvedRepeatPath,
        instances = updatedList,
      )
    val updatedRecord = state.recordInstance.copy(data_ = updatedRawNode)
    return evaluateInternal(
      pass = EvaluationPass(compiled, state.activeLanguage, environment),
      baseRecord = updatedRecord,
      rawRecordNode = updatedRawNode,
    )
  }

  /**
   * Applies key-value results returned by an external platform intent (`IntentConfig`) to the
   * mapped fields in the record and recalculates [FormState].
   */
  fun applyIntentResponse(
    state: FormState,
    contextCanonicalPath: String,
    responseMappings: Map<String, String>,
    intentResults: Map<String, TypedValue>,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState {
    var currentState = state
    val compiled = compile(state.formDef)
    val relContext = RecordMutator.normalizeSchemaPath(contextCanonicalPath, compiled.rootAliases)
    val contextSchema = compiled.schemaByRelativePath[relContext]
    val containerCanonicalPath =
      if (contextSchema != null && contextSchema.type != DataType.TYPE_MESSAGE) {
        contextCanonicalPath.substringBeforeLast('/', "/${compiled.rootName}")
      } else {
        contextCanonicalPath
      }
    for ((resultKey, targetFieldRef) in responseMappings) {
      val resultValue = intentResults[resultKey] ?: continue
      val targetCanonical =
        RecordMutator.resolveCanonicalPath(
          rawPath = targetFieldRef,
          rootName = compiled.rootName,
          rootAliases = compiled.rootAliases,
          contextCanonicalPath = containerCanonicalPath,
        )
      currentState =
        updateFieldValue(
          compiled = compiled,
          state = currentState,
          canonicalPath = targetCanonical,
          newValue = FieldValue(scalar_value = resultValue),
          environment = environment,
        )
    }
    return currentState
  }

  /** Switches the active translation language and re-resolves all localized labels and choices. */
  fun setLanguage(
    state: FormState,
    language: String,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState = setLanguage(compile(state.formDef), state, language, environment)

  /** [setLanguage] reusing [compiled], which must have been compiled from `state.formDef`. */
  internal fun setLanguage(
    compiled: CompiledForm,
    state: FormState,
    language: String,
    environment: FormEnvironment,
  ): FormState {
    requireCompiledFrom(compiled, state)
    return evaluateInternal(
      pass = EvaluationPass(compiled, language, environment, state.pendingRequests),
      baseRecord = state.recordInstance,
      rawRecordNode = state.rawRecordNode,
    )
  }

  /**
   * Finalizes the form session: updates `end` timestamp preloads (`RecordMetadata.end_time`),
   * re-evaluates the form state, and returns [FinalizationResult.Success] if all relevant required
   * and constraint validations pass, or [FinalizationResult.ValidationFailure] otherwise.
   */
  fun finalize(
    state: FormState,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FinalizationResult = finalize(compile(state.formDef), state, environment)

  /** [finalize] reusing [compiled], which must have been compiled from `state.formDef`. */
  internal fun finalize(
    compiled: CompiledForm,
    state: FormState,
    environment: FormEnvironment,
  ): FinalizationResult {
    requireCompiledFrom(compiled, state)
    val pass = EvaluationPass(compiled, state.activeLanguage, environment)
    val (updatedRawNode, updatedMetadata) =
      PreloadExecutor(pass)
        .execute(
          rawNode = state.rawRecordNode,
          metadata = state.recordInstance.metadata ?: RecordMetadata(),
          isFirstLoad = false,
          isFinalize = true,
        )
    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)
    val finalState =
      evaluateInternal(pass = pass, baseRecord = updatedRecord, rawRecordNode = updatedRawNode)
    return if (finalState.isValid) {
      FinalizationResult.Success(
        recordInstance = finalState.recordInstance,
        entityStates = finalState.entityStates.filter { it.isActive },
        state = finalState,
      )
    } else {
      FinalizationResult.ValidationFailure(errors = finalState.validationErrors, state = finalState)
    }
  }

  /**
   * Guards the internal overloads against evaluating a state with another form's compiled rules.
   * States produced from [compiled] carry its exact [FormDef] instance, so identity is enough and
   * avoids a deep comparison of the form on every edit.
   */
  private fun requireCompiledFrom(compiled: CompiledForm, state: FormState) {
    require(compiled.formDef === state.formDef) {
      "CompiledForm was compiled from a different FormDef than this FormState's"
    }
  }

  // ===============================================================================================
  // Internal Evaluation Pipeline
  // ===============================================================================================

  private fun evaluateInternal(
    pass: EvaluationPass,
    baseRecord: RecordInstance,
    rawRecordNode: RecordNode,
  ): FormState {
    val compiled = pass.compiled
    val record = ConvergenceLoop(pass).converge(baseRecord, rawRecordNode)
    val effectiveRecord =
      baseRecord.copy(
        form_id = baseRecord.form_id.ifEmpty { compiled.formDef.form_id },
        form_version = baseRecord.form_version.ifEmpty { compiled.formDef.version },
        metadata = record.metadata,
        data_ = record.prunedNode,
      )
    val rootEvalContext = pass.evaluationContext(effectiveRecord)

    val (fieldStates, validationErrors) =
      FieldStateEvaluator(
          pass = pass,
          effectiveRecord = effectiveRecord,
          rawNode = record.rawNode,
          relevancyMap = record.relevancyMap,
          rootEvalContext = rootEvalContext,
        )
        .evaluate()
    val rootComponents =
      ViewMaterializer(
          pass = pass,
          fieldStates = fieldStates,
          relevancyMap = record.relevancyMap,
          dynamicRepeatCounts = record.repeatCounts,
          effectiveRecord = effectiveRecord,
          rootEvalContext = rootEvalContext,
        )
        .materialize()
    val entityStates =
      EntityStateEvaluator(compiled)
        .evaluate(fieldStates = fieldStates, rootEvalContext = rootEvalContext)

    return FormState(
      formDef = compiled.formDef,
      recordInstance = effectiveRecord,
      rawRecordNode = record.rawNode,
      activeLanguage = pass.activeLanguage,
      availableLanguages =
        (compiled.formDef.model?.translations?.languages ?: emptyList()).map { it.language },
      fieldStates = fieldStates,
      rootComponents = rootComponents,
      entityStates = entityStates,
      validationErrors = validationErrors,
      pendingRequests = pass.pendingRequests.toList(),
      didNotConverge = !record.converged,
    )
  }

  private fun resolveActiveLanguage(formDef: FormDef, requestedLanguage: String?): String {
    if (!requestedLanguage.isNullOrBlank()) return requestedLanguage
    if (formDef.default_language.isNotBlank()) return formDef.default_language
    val catalog = formDef.model?.translations
    return catalog?.languages?.find { it.is_default }?.language
      ?: catalog?.languages?.firstOrNull()?.language
      ?: "default"
  }

  private fun coerceFieldValueToType(fv: FieldValue?, targetType: DataType): FieldValue? {
    if (fv == null) return null
    if (fv.list_value != null || fv.node_value != null || fv.repeat_value != null) return fv
    val scalar = fv.scalar_value ?: return fv
    if (targetType == DataType.DATA_TYPE_UNSPECIFIED) return fv
    val xpVal = XPathValue.fromTypedValue(scalar)
    return xpVal.toFieldValue(targetType) ?: fv
  }
}
