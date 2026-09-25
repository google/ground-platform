/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.engine

import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.IntentConfig
import groundplatform.v2.forms.ItemsetDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.LocalizedString
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.ViewComponent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.EntityState
import org.groundplatform.v2.core.forms.model.FieldState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState
import org.groundplatform.v2.core.forms.model.PlatformEffectRequest
import org.groundplatform.v2.core.forms.model.RepeatInstanceState
import org.groundplatform.v2.core.forms.model.ResolvedChoiceOption
import org.groundplatform.v2.core.forms.model.ResolvedIntent
import org.groundplatform.v2.core.forms.model.ResolvedLabel
import org.groundplatform.v2.core.forms.model.ValidationError
import org.groundplatform.v2.core.forms.model.ValidationErrorKind
import org.groundplatform.v2.core.forms.model.ValidationStatus
import org.groundplatform.v2.core.forms.xpath.EvaluationContext
import org.groundplatform.v2.core.forms.xpath.SecondaryInstanceProvider
import org.groundplatform.v2.core.forms.xpath.XPathException
import org.groundplatform.v2.core.forms.xpath.model.TemporalUtils
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Stateless evaluation engine for ProtoForms and XForms specifications.
 *
 * Evaluates dynamic form states (`relevant`, `calculate`, `required`, `constraint`, `jr:count`
 * repeats, cascading `itemset` queries, localized `<output>` interpolations, preloads, lifecycle
 * events/actions, and XForms Entities) in pure Kotlin Multiplatform.
 */
object FormEngine {

  private const val MAX_CONVERGENCE_PASSES = 5

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
  ): FormState {
    val compiled = compile(formDef)
    val schema = formDef.model?.primary_instance?.record_schema
    val defaultValues = formDef.model?.primary_instance?.default_values

    var rawNode =
      RecordMutator.buildInitialRecordNode(
        schema = schema,
        defaultValues = defaultValues,
        existingNode = existingRecord?.data_,
      )
    var metadata = existingRecord?.metadata ?: formDef.model?.metadata ?: RecordMetadata()
    val pendingRequests = mutableListOf<PlatformEffectRequest>()

    // 1. Run preloads for initialization
    val preloaded =
      executePreloads(
        compiled = compiled,
        rawNode = rawNode,
        metadata = metadata,
        isFirstLoad = isFirstLoad,
        isFinalize = false,
        environment = environment,
      )
    rawNode = preloaded.first
    metadata = preloaded.second

    // 2. Dispatch EVENT_INSTANCE_FIRST_LOAD and EVENT_INSTANCE_LOAD actions
    val startupEvents = buildList {
      if (isFirstLoad) add(EventType.EVENT_INSTANCE_FIRST_LOAD)
      add(EventType.EVENT_INSTANCE_LOAD)
    }
    val modelActions =
      (formDef.model?.actions ?: emptyList()).filter { action ->
        action.events.any { it in startupEvents }
      }
    if (modelActions.isNotEmpty()) {
      val afterActions =
        executeActions(
          compiled = compiled,
          actions = modelActions,
          rawNode = rawNode,
          metadata = metadata,
          contextCanonicalPath = "/${compiled.rootName}",
          activeLanguage = resolveActiveLanguage(formDef, activeLanguage),
          environment = environment,
          pendingRequests = pendingRequests,
        )
      rawNode = afterActions.first
      if (afterActions.second != null) metadata = afterActions.second!!
    }

    val initialRecord =
      RecordInstance(
        form_id = existingRecord?.form_id?.takeIf { it.isNotEmpty() } ?: formDef.form_id,
        form_version = existingRecord?.form_version?.takeIf { it.isNotEmpty() } ?: formDef.version,
        metadata = metadata,
        data_ = rawNode,
        audit_log = existingRecord?.audit_log,
      )

    return evaluateInternal(
      compiled = compiled,
      baseRecord = initialRecord,
      rawRecordNode = rawNode,
      activeLanguage = resolveActiveLanguage(formDef, activeLanguage),
      pendingRequests = pendingRequests,
      environment = environment,
    )
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
    val compiled = compile(formDef)
    val rawNode = recordInstance.data_ ?: RecordNode()
    return evaluateInternal(
      compiled = compiled,
      baseRecord = recordInstance,
      rawRecordNode = rawNode,
      activeLanguage = resolveActiveLanguage(formDef, activeLanguage),
      pendingRequests = emptyList(),
      environment = environment,
    )
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
  ): FormState {
    val compiled = compile(state.formDef)
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

    val pendingRequests = mutableListOf<PlatformEffectRequest>()

    // Trigger EVENT_VALUE_CHANGED actions bound to this control/field
    val watchedActions = compiled.valueChangedActionsBySourcePath[relPath] ?: emptyList()
    if (watchedActions.isNotEmpty()) {
      val parentContextPath = resolvedPath.substringBeforeLast('/', "/${compiled.rootName}")
      val afterActions =
        executeActions(
          compiled = compiled,
          actions = watchedActions,
          rawNode = updatedRawNode,
          metadata = updatedMetadata,
          contextCanonicalPath = parentContextPath,
          activeLanguage = state.activeLanguage,
          environment = environment,
          pendingRequests = pendingRequests,
        )
      updatedRawNode = afterActions.first
      updatedMetadata = afterActions.second
    }

    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)

    return evaluateInternal(
      compiled = compiled,
      baseRecord = updatedRecord,
      rawRecordNode = updatedRawNode,
      activeLanguage = state.activeLanguage,
      pendingRequests = pendingRequests,
      environment = environment,
    )
  }

  /**
   * Appends a new repeat instance to the repeat group at [repeatPath], initializes its schema
   * defaults, fires `EVENT_REPEAT_INSERT` (`odk-new-repeat`) actions, and recalculates [FormState].
   */
  fun addRepeatInstance(
    state: FormState,
    repeatPath: String,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
  ): FormState {
    val compiled = compile(state.formDef)
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
    val pendingRequests = mutableListOf<PlatformEffectRequest>()

    // Trigger EVENT_REPEAT_INSERT actions scoped to the newly added repeat instance
    val repeatActions = compiled.repeatInsertActionsByRepeatPath[relRepeatPath] ?: emptyList()
    if (repeatActions.isNotEmpty()) {
      val instanceCanonicalPath = "$resolvedRepeatPath[$newIndex1Based]"
      val afterActions =
        executeActions(
          compiled = compiled,
          actions = repeatActions,
          rawNode = updatedRawNode,
          metadata = updatedMetadata,
          contextCanonicalPath = instanceCanonicalPath,
          activeLanguage = state.activeLanguage,
          environment = environment,
          pendingRequests = pendingRequests,
        )
      updatedRawNode = afterActions.first
      updatedMetadata = afterActions.second
    }

    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)

    return evaluateInternal(
      compiled = compiled,
      baseRecord = updatedRecord,
      rawRecordNode = updatedRawNode,
      activeLanguage = state.activeLanguage,
      pendingRequests = pendingRequests,
      environment = environment,
    )
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
  ): FormState {
    val compiled = compile(state.formDef)
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
      compiled = compiled,
      baseRecord = updatedRecord,
      rawRecordNode = updatedRawNode,
      activeLanguage = state.activeLanguage,
      pendingRequests = emptyList(),
      environment = environment,
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
  ): FormState {
    val compiled = compile(state.formDef)
    return evaluateInternal(
      compiled = compiled,
      baseRecord = state.recordInstance,
      rawRecordNode = state.rawRecordNode,
      activeLanguage = language,
      pendingRequests = state.pendingRequests,
      environment = environment,
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
  ): FinalizationResult {
    val compiled = compile(state.formDef)
    val (updatedRawNode, updatedMetadata) =
      executePreloads(
        compiled = compiled,
        rawNode = state.rawRecordNode,
        metadata = state.recordInstance.metadata ?: RecordMetadata(),
        isFirstLoad = false,
        isFinalize = true,
        environment = environment,
      )
    val updatedRecord =
      state.recordInstance.copy(metadata = updatedMetadata, data_ = updatedRawNode)
    val finalState =
      evaluateInternal(
        compiled = compiled,
        baseRecord = updatedRecord,
        rawRecordNode = updatedRawNode,
        activeLanguage = state.activeLanguage,
        pendingRequests = emptyList(),
        environment = environment,
      )
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

  // ===============================================================================================
  // Internal Evaluation Pipeline
  // ===============================================================================================

  private fun evaluateInternal(
    compiled: CompiledForm,
    baseRecord: RecordInstance,
    rawRecordNode: RecordNode,
    activeLanguage: String,
    pendingRequests: List<PlatformEffectRequest>,
    environment: FormEnvironment,
  ): FormState {
    var workingRawNode = rawRecordNode
    var workingMetadata = baseRecord.metadata ?: RecordMetadata()
    val allPendingRequests = pendingRequests.toMutableList()
    val secondaryProvider =
      environment.secondaryInstanceProvider ?: compiled.inlineSecondaryInstanceProvider

    var relevancyMap = linkedMapOf<String, Boolean>()
    var prunedNode = workingRawNode
    val dynamicRepeatCounts = mutableMapOf<String, Int>()
    var converged = false

    // Multi-pass convergence loop for repeat counts, top-down relevancy, and calculations
    for (pass in 0 until MAX_CONVERGENCE_PASSES) {
      val prevPruned = prunedNode
      val prevRelevancy = relevancyMap.toMap()
      val prevCounts = dynamicRepeatCounts.toMap()

      // 1. Reconcile dynamic repeat counts (RepeatDef.count_expression)
      val repeatResult =
        reconcileRepeatCounts(
          compiled = compiled,
          rawNode = workingRawNode,
          prunedNode = prunedNode,
          metadata = workingMetadata,
          baseRecord = baseRecord,
          activeLanguage = activeLanguage,
          secondaryProvider = secondaryProvider,
          environment = environment,
          pendingRequests = allPendingRequests,
        )
      workingRawNode = repeatResult.updatedRawNode
      workingMetadata = repeatResult.updatedMetadata
      dynamicRepeatCounts.clear()
      dynamicRepeatCounts.putAll(repeatResult.evaluatedCounts)

      // 2. Top-down relevancy pass
      relevancyMap =
        evaluateRelevancyMap(
          compiled = compiled,
          rawNode = workingRawNode,
          currentPrunedNode = prunedNode,
          metadata = workingMetadata,
          baseRecord = baseRecord,
          activeLanguage = activeLanguage,
          secondaryProvider = secondaryProvider,
          environment = environment,
        )

      prunedNode =
        RecordMutator.pruneNonRelevantNodes(
          node = workingRawNode,
          currentPathPrefix = "/${compiled.rootName}",
          relevancyMap = relevancyMap,
        )

      // 3. Topological calculate_expression pass
      val calcResult =
        evaluateCalculations(
          compiled = compiled,
          rawNode = workingRawNode,
          prunedNode = prunedNode,
          metadata = workingMetadata,
          relevancyMap = relevancyMap,
          baseRecord = baseRecord,
          activeLanguage = activeLanguage,
          secondaryProvider = secondaryProvider,
          environment = environment,
        )
      workingRawNode = calcResult.first
      workingMetadata = calcResult.second
      prunedNode =
        RecordMutator.pruneNonRelevantNodes(
          node = workingRawNode,
          currentPathPrefix = "/${compiled.rootName}",
          relevancyMap = relevancyMap,
        )

      if (
        prunedNode == prevPruned &&
          relevancyMap == prevRelevancy &&
          dynamicRepeatCounts == prevCounts
      ) {
        converged = true
        break
      }
    }

    val effectiveRecord =
      baseRecord.copy(
        form_id = baseRecord.form_id.ifEmpty { compiled.formDef.form_id },
        form_version = baseRecord.form_version.ifEmpty { compiled.formDef.version },
        metadata = workingMetadata,
        data_ = prunedNode,
      )

    val rootEvalContext =
      createEvaluationContext(
        compiled = compiled,
        recordInstance = effectiveRecord,
        activeLanguage = activeLanguage,
        secondaryProvider = secondaryProvider,
        environment = environment,
      )

    // 4. Evaluate field states & validation rules (required, constraint, range)
    val (fieldStates, validationErrors) =
      evaluateFieldStatesAndValidations(
        compiled = compiled,
        effectiveRecord = effectiveRecord,
        rawNode = workingRawNode,
        relevancyMap = relevancyMap,
        rootEvalContext = rootEvalContext,
        activeLanguage = activeLanguage,
      )

    // 5. Materialize repeat-expanded UI view component hierarchy
    val rootComponents =
      materializeViewComponents(
        compiled = compiled,
        components = compiled.formDef.view?.components ?: emptyList(),
        parentCanonicalPath = "/${compiled.rootName}",
        parentRelativePath = "",
        parentRelevant = true,
        fieldStates = fieldStates,
        relevancyMap = relevancyMap,
        dynamicRepeatCounts = dynamicRepeatCounts,
        effectiveRecord = effectiveRecord,
        rootEvalContext = rootEvalContext,
        activeLanguage = activeLanguage,
        environment = environment,
      )

    // 6. Evaluate EntityDeclarations
    val entityStates =
      evaluateEntityStates(
        compiled = compiled,
        fieldStates = fieldStates,
        rootEvalContext = rootEvalContext,
      )

    val availableLanguages =
      (compiled.formDef.model?.translations?.languages ?: emptyList()).map { it.language }

    return FormState(
      formDef = compiled.formDef,
      recordInstance = effectiveRecord,
      rawRecordNode = workingRawNode,
      activeLanguage = activeLanguage,
      availableLanguages = availableLanguages,
      fieldStates = fieldStates,
      rootComponents = rootComponents,
      entityStates = entityStates,
      validationErrors = validationErrors,
      pendingRequests = allPendingRequests,
      didNotConverge = !converged,
    )
  }

  private fun executePreloads(
    compiled: CompiledForm,
    rawNode: RecordNode,
    metadata: RecordMetadata,
    isFirstLoad: Boolean,
    isFinalize: Boolean,
    environment: FormEnvironment,
  ): Pair<RecordNode, RecordMetadata> {
    var currentRaw = rawNode
    var currentMeta = metadata
    val epochMillis = environment.clockEpochMillis()
    val epochSeconds = floor(epochMillis / 1000.0).toLong()
    val nanos = ((epochMillis % 1000L + 1000L) % 1000L) * 1_000_000L
    val currentInstant = ofEpochSecond(epochSeconds, nanos)
    val epochDays = floor(epochSeconds / 86400.0).toLong()
    val currentDate = TemporalUtils.epochDaysToDate(epochDays)
    val remSec = ((epochSeconds % 86400L) + 86400L) % 86400L
    val currentTime =
      com.google.type.TimeOfDay(
        hours = (remSec / 3600L).toInt(),
        minutes = ((remSec % 3600L) / 60L).toInt(),
        seconds = (remSec % 60L).toInt(),
        nanos = nanos.toInt(),
      )

    // Ensure RecordMetadata.instance_id is populated on first load
    if (isFirstLoad && currentMeta.instance_id.isEmpty()) {
      val rawUuid = environment.uuidGenerator()
      val formattedUid = if (rawUuid.startsWith("uuid:")) rawUuid else "uuid:$rawUuid"
      currentMeta = currentMeta.copy(instance_id = formattedUid)
    }
    if (isFirstLoad && currentMeta.start_time == null) {
      currentMeta = currentMeta.copy(start_time = currentInstant)
    }
    if (isFirstLoad && currentMeta.today == null) {
      currentMeta = currentMeta.copy(today = currentInstant)
    }
    if (isFinalize) {
      currentMeta = currentMeta.copy(end_time = currentInstant)
    }

    // Populate device properties on metadata if provided in environment
    environment.getDeviceProperty("deviceid")?.let {
      if (currentMeta.device_id.isEmpty()) currentMeta = currentMeta.copy(device_id = it)
    }
    environment.getDeviceProperty("subscriberid")?.let {
      if (currentMeta.subscriber_id.isEmpty()) currentMeta = currentMeta.copy(subscriber_id = it)
    }
    environment.getDeviceProperty("simserial")?.let {
      if (currentMeta.sim_serial.isEmpty()) currentMeta = currentMeta.copy(sim_serial = it)
    }
    environment.getDeviceProperty("phonenumber")?.let {
      if (currentMeta.phone_number.isEmpty()) currentMeta = currentMeta.copy(phone_number = it)
    }

    // Evaluate explicit FieldBinding preloads
    for (compiledBinding in compiled.bindingsByRelativePath.values) {
      val b = compiledBinding.binding
      if (b.preload == PreloadType.PRELOAD_UNSPECIFIED) continue
      val param = b.preload_param.trim().lowercase()
      val canonicalPath = "/${compiled.rootName}/${compiledBinding.relativePath}"
      val existingVal =
        RecordMutator.getFieldValue(currentRaw, currentMeta, compiled.rootAliases, canonicalPath)
      val isExistingEmpty = RecordMutator.isFieldValueEmpty(existingVal)

      val preloadTypedValue: TypedValue? =
        when (b.preload) {
          PreloadType.PRELOAD_UID -> {
            if (isFirstLoad && isExistingEmpty) {
              val uid =
                currentMeta.instance_id.ifEmpty {
                  val generated = environment.uuidGenerator()
                  if (generated.startsWith("uuid:")) generated else "uuid:$generated"
                }
              TypedValue(string_value = uid)
            } else null
          }
          PreloadType.PRELOAD_TIMESTAMP -> {
            when (param) {
              "start",
              "" ->
                if (isFirstLoad && isExistingEmpty) TypedValue(timestamp_value = currentInstant)
                else null
              "end" ->
                if (isFinalize || isFirstLoad) TypedValue(timestamp_value = currentInstant)
                else null
              else -> null
            }
          }
          PreloadType.PRELOAD_DATE -> {
            if (isFirstLoad && isExistingEmpty) TypedValue(date_value = currentDate) else null
          }
          PreloadType.PRELOAD_TIME -> {
            if (isFirstLoad && isExistingEmpty) TypedValue(time_value = currentTime) else null
          }
          PreloadType.PRELOAD_PROPERTY -> {
            if (isFirstLoad && isExistingEmpty) {
              environment.getDeviceProperty(param)?.let { TypedValue(string_value = it) }
            } else null
          }
          PreloadType.PRELOAD_CONTEXT -> {
            if (isFirstLoad && isExistingEmpty) {
              environment.contextParams[b.preload_param]?.let { TypedValue(string_value = it) }
            } else null
          }
          PreloadType.PRELOAD_UNSPECIFIED -> null
        }

      if (preloadTypedValue != null) {
        val (nextRaw, nextMeta) =
          RecordMutator.setFieldValue(
            rootNode = currentRaw,
            metadata = currentMeta,
            rootAliases = compiled.rootAliases,
            canonicalPath = canonicalPath,
            newValue = FieldValue(scalar_value = preloadTypedValue),
          )
        currentRaw = nextRaw
        if (nextMeta != null) currentMeta = nextMeta
      }
    }

    return currentRaw to currentMeta
  }

  private fun executeActions(
    compiled: CompiledForm,
    actions: List<ActionDef>,
    rawNode: RecordNode,
    metadata: RecordMetadata?,
    contextCanonicalPath: String,
    activeLanguage: String,
    environment: FormEnvironment,
    pendingRequests: MutableList<PlatformEffectRequest>,
  ): Pair<RecordNode, RecordMetadata?> {
    var currentRaw = rawNode
    var currentMeta = metadata
    val secondaryProvider =
      environment.secondaryInstanceProvider ?: compiled.inlineSecondaryInstanceProvider

    for (action in actions) {
      val targetCanonical =
        RecordMutator.resolveCanonicalPath(
          rawPath = action.target_field,
          rootName = compiled.rootName,
          rootAliases = compiled.rootAliases,
          contextCanonicalPath = contextCanonicalPath,
        )
      val targetRel = RecordMutator.normalizeSchemaPath(targetCanonical, compiled.rootAliases)
      val targetType = compiled.resolveDataType(targetRel)

      when {
        action.type == ActionType.ACTION_SET_GEOPOINT -> {
          pendingRequests.add(PlatformEffectRequest.SetGeopointRequest(targetCanonical))
        }
        action.type == ActionType.ACTION_SET_VALUE ||
          action.value_expression.isNotEmpty() ||
          action.literal_value != null -> {
          val newFv: FieldValue? =
            if (action.value_expression.isNotEmpty()) {
              val tempRecord =
                RecordInstance(
                  form_id = compiled.formDef.form_id,
                  form_version = compiled.formDef.version,
                  metadata = currentMeta,
                  data_ = currentRaw,
                )
              val evalCtx =
                createEvaluationContext(
                  compiled = compiled,
                  recordInstance = tempRecord,
                  activeLanguage = activeLanguage,
                  secondaryProvider = secondaryProvider,
                  environment = environment,
                  contextCanonicalPath = targetCanonical,
                )
              val expr = compiled.compileExpression(action.value_expression)
              expr?.evaluateFieldValue(evalCtx, targetType)
            } else if (action.literal_value != null) {
              FieldValue(scalar_value = action.literal_value)
            } else {
              null
            }

          if (newFv != null) {
            val (nextRaw, nextMeta) =
              RecordMutator.setFieldValue(
                rootNode = currentRaw,
                metadata = currentMeta,
                rootAliases = compiled.rootAliases,
                canonicalPath = targetCanonical,
                newValue = newFv,
              )
            currentRaw = nextRaw
            currentMeta = nextMeta
          }
        }
      }
    }

    return currentRaw to currentMeta
  }

  private data class RepeatReconciliationResult(
    val updatedRawNode: RecordNode,
    val updatedMetadata: RecordMetadata,
    val evaluatedCounts: Map<String, Int>,
  )

  private fun reconcileRepeatCounts(
    compiled: CompiledForm,
    rawNode: RecordNode,
    prunedNode: RecordNode,
    metadata: RecordMetadata,
    baseRecord: RecordInstance,
    activeLanguage: String,
    secondaryProvider: SecondaryInstanceProvider,
    environment: FormEnvironment,
    pendingRequests: MutableList<PlatformEffectRequest>,
  ): RepeatReconciliationResult {
    var currentRaw = rawNode
    var currentMeta = metadata
    val evaluatedCounts = mutableMapOf<String, Int>()

    for ((relRepeatPath, repeatDef) in compiled.repeatDefsByRelativePath) {
      val countExpr = compiled.compileExpression(repeatDef.count_expression) ?: continue
      val repeatSchema = compiled.schemaByRelativePath[relRepeatPath]

      // Expand parent paths if this repeat is nested inside another repeat
      val parentRelPath = relRepeatPath.substringBeforeLast('/', "")
      val repeatName = relRepeatPath.substringAfterLast('/')
      val parentCanonicalPaths =
        expandConcretePathsForRelativePath(
          rootNode = currentRaw,
          rootName = compiled.rootName,
          relativePath = parentRelPath,
        )

      for (parentCanonical in parentCanonicalPaths) {
        val repeatCanonicalPath = "$parentCanonical/$repeatName"
        val tempRecord = baseRecord.copy(metadata = currentMeta, data_ = prunedNode)
        val evalCtx =
          createEvaluationContext(
            compiled = compiled,
            recordInstance = tempRecord,
            activeLanguage = activeLanguage,
            secondaryProvider = secondaryProvider,
            environment = environment,
            contextCanonicalPath = parentCanonical,
          )
        val countDouble = countExpr.evaluateNumber(evalCtx)
        val targetCount = if (countDouble.isNaN()) 0 else max(0, countDouble.roundToInt())
        evaluatedCounts[repeatCanonicalPath] = targetCount

        val existing =
          RecordMutator.getRepeatInstances(
            rootNode = currentRaw,
            rootAliases = compiled.rootAliases,
            repeatCanonicalPath = repeatCanonicalPath,
          )

        if (existing.size < targetCount) {
          val mutableList = existing.toMutableList()
          val startIndex = existing.size + 1
          for (newIdx in startIndex..targetCount) {
            mutableList.add(RecordMutator.buildDefaultRepeatItemNode(repeatSchema))
          }
          currentRaw =
            RecordMutator.setRepeatInstances(
              rootNode = currentRaw,
              rootAliases = compiled.rootAliases,
              repeatCanonicalPath = repeatCanonicalPath,
              instances = mutableList,
            )

          // Fire EVENT_REPEAT_INSERT actions for each newly auto-created repeat instance
          val repeatActions = compiled.repeatInsertActionsByRepeatPath[relRepeatPath] ?: emptyList()
          if (repeatActions.isNotEmpty()) {
            for (newIdx in startIndex..targetCount) {
              val instanceCanonical = "$repeatCanonicalPath[$newIdx]"
              val afterActions =
                executeActions(
                  compiled = compiled,
                  actions = repeatActions,
                  rawNode = currentRaw,
                  metadata = currentMeta,
                  contextCanonicalPath = instanceCanonical,
                  activeLanguage = activeLanguage,
                  environment = environment,
                  pendingRequests = pendingRequests,
                )
              currentRaw = afterActions.first
              if (afterActions.second != null) currentMeta = afterActions.second!!
            }
          }
        } else if (existing.size > targetCount) {
          currentRaw =
            RecordMutator.setRepeatInstances(
              rootNode = currentRaw,
              rootAliases = compiled.rootAliases,
              repeatCanonicalPath = repeatCanonicalPath,
              instances = existing.take(targetCount),
            )
        }
      }
    }

    return RepeatReconciliationResult(currentRaw, currentMeta, evaluatedCounts)
  }

  private fun evaluateRelevancyMap(
    compiled: CompiledForm,
    rawNode: RecordNode,
    currentPrunedNode: RecordNode,
    metadata: RecordMetadata,
    baseRecord: RecordInstance,
    activeLanguage: String,
    secondaryProvider: SecondaryInstanceProvider,
    environment: FormEnvironment,
  ): LinkedHashMap<String, Boolean> {
    val relevancyMap = linkedMapOf<String, Boolean>()
    val rootCanonical = "/${compiled.rootName}"
    relevancyMap[rootCanonical] = true

    val tempRecord = baseRecord.copy(metadata = metadata, data_ = currentPrunedNode)
    val baseEvalCtx =
      createEvaluationContext(
        compiled = compiled,
        recordInstance = tempRecord,
        activeLanguage = activeLanguage,
        secondaryProvider = secondaryProvider,
        environment = environment,
      )

    // Collect all relative paths from schema and bindings, ordered top-down by segment depth
    val allRelativePaths =
      (compiled.schemaByRelativePath.keys + compiled.bindingsByRelativePath.keys)
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.count { ch -> ch == '/' } }

    for (relPath in allRelativePaths) {
      val concretePaths =
        expandConcretePathsForRelativePath(
          rootNode = rawNode,
          rootName = compiled.rootName,
          relativePath = relPath,
        )
      val compiledBinding = compiled.bindingsByRelativePath[relPath]
      for (canonicalPath in concretePaths) {
        val parentCanonical = canonicalPath.substringBeforeLast('/', rootCanonical)
        val parentRelevant = relevancyMap[parentCanonical] ?: true
        if (!parentRelevant) {
          relevancyMap[canonicalPath] = false
          continue
        }
        val relExpr = compiledBinding?.relevantExpr
        if (relExpr == null) {
          relevancyMap[canonicalPath] = true
        } else {
          val nodeCtx = positionContextAtPath(baseEvalCtx, canonicalPath)
          val isRel = relExpr.evaluateBoolean(nodeCtx)
          relevancyMap[canonicalPath] = isRel
          // If this is a repeat group with 1 instance, also record unindexed path alias
          if (canonicalPath.endsWith("[1]")) {
            val unindexed = canonicalPath.removeSuffix("[1]")
            if (!relevancyMap.containsKey(unindexed)) {
              relevancyMap[unindexed] = isRel
            }
          }
        }
      }
    }

    return relevancyMap
  }

  private fun evaluateCalculations(
    compiled: CompiledForm,
    rawNode: RecordNode,
    prunedNode: RecordNode,
    metadata: RecordMetadata,
    relevancyMap: Map<String, Boolean>,
    baseRecord: RecordInstance,
    activeLanguage: String,
    secondaryProvider: SecondaryInstanceProvider,
    environment: FormEnvironment,
  ): Pair<RecordNode, RecordMetadata> {
    var currentRaw = rawNode
    var currentPruned = prunedNode
    var currentMeta = metadata

    for (compiledBinding in compiled.topologicalBindings) {
      val calcExpr = compiledBinding.calculateExpr ?: continue
      val relPath = compiledBinding.relativePath
      val targetType = compiled.resolveDataType(relPath)

      val concretePaths =
        expandConcretePathsForRelativePath(
          rootNode = currentRaw,
          rootName = compiled.rootName,
          relativePath = relPath,
        )

      for (canonicalPath in concretePaths) {
        val isRelevant = relevancyMap[canonicalPath] ?: true
        if (!isRelevant) continue

        val tempRecord = baseRecord.copy(metadata = currentMeta, data_ = currentPruned)
        val baseEvalCtx =
          createEvaluationContext(
            compiled = compiled,
            recordInstance = tempRecord,
            activeLanguage = activeLanguage,
            secondaryProvider = secondaryProvider,
            environment = environment,
          )
        val nodeCtx = positionContextAtPath(baseEvalCtx, canonicalPath)
        val calculatedFv = calcExpr.evaluateFieldValue(nodeCtx, targetType)
        val existingFv =
          RecordMutator.getFieldValue(
            rootNode = currentRaw,
            metadata = currentMeta,
            rootAliases = compiled.rootAliases,
            canonicalPath = canonicalPath,
          )

        if (calculatedFv != existingFv) {
          val (nextRaw, nextMeta) =
            RecordMutator.setFieldValue(
              rootNode = currentRaw,
              metadata = currentMeta,
              rootAliases = compiled.rootAliases,
              canonicalPath = canonicalPath,
              newValue = calculatedFv,
            )
          currentRaw = nextRaw
          if (nextMeta != null) currentMeta = nextMeta
          currentPruned =
            RecordMutator.pruneNonRelevantNodes(
              node = currentRaw,
              currentPathPrefix = "/${compiled.rootName}",
              relevancyMap = relevancyMap,
            )
        }
      }
    }

    return currentRaw to currentMeta
  }

  private fun evaluateFieldStatesAndValidations(
    compiled: CompiledForm,
    effectiveRecord: RecordInstance,
    rawNode: RecordNode,
    relevancyMap: Map<String, Boolean>,
    rootEvalContext: EvaluationContext,
    activeLanguage: String,
  ): Pair<Map<String, FieldState>, List<ValidationError>> {
    val fieldStates = linkedMapOf<String, FieldState>()
    val allErrors = mutableListOf<ValidationError>()

    // Index any RangeConfig from ViewDef controls by relative path
    val rangeConfigsByRelPath = collectRangeConfigs(compiled)

    val allRelativePaths =
      (compiled.schemaByRelativePath.keys + compiled.bindingsByRelativePath.keys)
        .filter { it.isNotEmpty() }
        .distinct()

    for (relPath in allRelativePaths) {
      val compiledBinding = compiled.bindingsByRelativePath[relPath]
      val binding = compiledBinding?.binding
      val dataType = compiled.resolveDataType(relPath)
      val concretePaths =
        expandConcretePathsForRelativePath(
          rootNode = rawNode,
          rootName = compiled.rootName,
          relativePath = relPath,
        )

      for (canonicalPath in concretePaths) {
        val isRelevant = relevancyMap[canonicalPath] ?: true
        val rawVal =
          RecordMutator.getFieldValue(
            rootNode = rawNode,
            metadata = effectiveRecord.metadata,
            rootAliases = compiled.rootAliases,
            canonicalPath = canonicalPath,
          )
        val effectiveVal =
          if (isRelevant) {
            RecordMutator.getFieldValue(
              rootNode = effectiveRecord.data_ ?: RecordNode(),
              metadata = effectiveRecord.metadata,
              rootAliases = compiled.rootAliases,
              canonicalPath = canonicalPath,
            )
          } else {
            null
          }

        val isEmpty = RecordMutator.isFieldValueEmpty(effectiveVal)
        val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)

        val isRequired =
          if (isRelevant && compiledBinding?.requiredExpr != null) {
            compiledBinding.requiredExpr.evaluateBoolean(nodeCtx)
          } else {
            false
          }
        val isReadOnly = binding?.read_only == true
        val isCalculated = compiledBinding?.calculateExpr != null

        val fieldErrors = mutableListOf<ValidationError>()
        if (isRelevant) {
          // 1. Required check
          if (isRequired && isEmpty) {
            val msg =
              resolveBindingMessage(
                rawMessage = binding?.required_message ?: "",
                defaultMessage = "This field is required.",
                compiled = compiled,
                evalContext = nodeCtx,
                activeLanguage = activeLanguage,
              )
            fieldErrors.add(
              ValidationError(
                fieldPath = canonicalPath,
                kind = ValidationErrorKind.REQUIRED_MISSING,
                message = msg,
              )
            )
          }

          // 2. Constraint check (strictly evaluated when non-empty)
          if (!isEmpty && compiledBinding?.constraintExpr != null) {
            val constraintPassed = compiledBinding.constraintExpr.evaluateBoolean(nodeCtx)
            if (!constraintPassed) {
              val msg =
                resolveBindingMessage(
                  rawMessage = binding?.constraint_message ?: "",
                  defaultMessage = "Value violates constraint: ${binding?.constraint_expression}",
                  compiled = compiled,
                  evalContext = nodeCtx,
                  activeLanguage = activeLanguage,
                )
              fieldErrors.add(
                ValidationError(
                  fieldPath = canonicalPath,
                  kind = ValidationErrorKind.CONSTRAINT_VIOLATED,
                  message = msg,
                )
              )
            }
          }

          // 3. RangeConfig bounds check (if applicable)
          val rangeConfig = rangeConfigsByRelPath[relPath]
          if (!isEmpty && rangeConfig != null && rangeConfig.start < rangeConfig.end) {
            val numVal = XPathValue.fromFieldValue(effectiveVal).toNumber()
            if (!numVal.isNaN() && (numVal < rangeConfig.start || numVal > rangeConfig.end)) {
              fieldErrors.add(
                ValidationError(
                  fieldPath = canonicalPath,
                  kind = ValidationErrorKind.RANGE_OUT_OF_BOUNDS,
                  message =
                    "Value $numVal must be between ${rangeConfig.start} and ${rangeConfig.end}.",
                )
              )
            }
          }
        }

        val validationStatus =
          if (fieldErrors.isEmpty()) {
            ValidationStatus.Valid
          } else {
            allErrors.addAll(fieldErrors)
            ValidationStatus.Invalid(fieldErrors)
          }

        val state =
          FieldState(
            canonicalPath = canonicalPath,
            relativePath = relPath,
            binding = binding,
            dataType = dataType,
            value = effectiveVal,
            rawValue = rawVal,
            isEmpty = isEmpty,
            isRelevant = isRelevant,
            isRequired = isRequired,
            isReadOnly = isReadOnly,
            isCalculated = isCalculated,
            validationStatus = validationStatus,
          )
        fieldStates[canonicalPath] = state
        // Also register unindexed alias if repeat index [1]
        if (canonicalPath.endsWith("[1]")) {
          val unindexed = canonicalPath.removeSuffix("[1]")
          if (!fieldStates.containsKey(unindexed)) {
            fieldStates[unindexed] = state
          }
        }
      }
    }

    return fieldStates to allErrors
  }

  private fun materializeViewComponents(
    compiled: CompiledForm,
    components: List<ViewComponent>,
    parentCanonicalPath: String,
    parentRelativePath: String,
    parentRelevant: Boolean,
    fieldStates: Map<String, FieldState>,
    relevancyMap: Map<String, Boolean>,
    dynamicRepeatCounts: Map<String, Int>,
    effectiveRecord: RecordInstance,
    rootEvalContext: EvaluationContext,
    activeLanguage: String,
    environment: FormEnvironment,
  ): List<ComponentState> {
    val result = mutableListOf<ComponentState>()

    for ((idx, comp) in components.withIndex()) {
      when {
        comp.control != null -> {
          val control = comp.control
          val relPath = compiled.resolveComponentRelativePath(control.field_ref, parentRelativePath)
          val canonicalPath =
            if (control.field_ref.isNotEmpty()) {
              RecordMutator.resolveCanonicalPath(
                rawPath = control.field_ref,
                rootName = compiled.rootName,
                rootAliases = compiled.rootAliases,
                contextCanonicalPath = parentCanonicalPath,
              )
            } else {
              "$parentCanonicalPath/_control_$idx"
            }

          val fieldState =
            fieldStates[canonicalPath]
              ?: FieldState(
                canonicalPath = canonicalPath,
                relativePath = relPath,
                binding = compiled.bindingsByRelativePath[relPath]?.binding,
                dataType = compiled.resolveDataType(relPath),
                value = null,
                rawValue = null,
                isEmpty = true,
                isRelevant = parentRelevant && (relevancyMap[canonicalPath] ?: true),
                isRequired = false,
                isReadOnly = control.type == ControlType.CONTROL_TRIGGER,
                isCalculated = false,
                validationStatus = ValidationStatus.Valid,
              )

          val isRelevant = parentRelevant && fieldState.isRelevant
          val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)
          val resolvedLabel = resolveLabel(control.label, compiled, nodeCtx, activeLanguage)
          val resolvedHint = resolveLabel(control.hint, compiled, nodeCtx, activeLanguage)
          val options =
            if (isRelevant) {
              resolveControlOptions(
                control = control,
                compiled = compiled,
                controlContext = nodeCtx,
                activeLanguage = activeLanguage,
                environment = environment,
              )
            } else {
              emptyList()
            }
          val resolvedIntent = control.intent?.let { resolveIntentConfig(it, compiled, nodeCtx) }

          result.add(
            ComponentState.ControlState(
              canonicalPath = canonicalPath,
              controlDef = control,
              fieldState = fieldState,
              isRelevant = isRelevant,
              label = resolvedLabel,
              hint = resolvedHint,
              appearance = control.appearance,
              options = options,
              resolvedIntent = resolvedIntent,
            )
          )
        }
        comp.group != null -> {
          val group = comp.group
          val relPath = compiled.resolveComponentRelativePath(group.field_ref, parentRelativePath)
          val canonicalPath =
            if (group.field_ref.isNotEmpty()) {
              RecordMutator.resolveCanonicalPath(
                rawPath = group.field_ref,
                rootName = compiled.rootName,
                rootAliases = compiled.rootAliases,
                contextCanonicalPath = parentCanonicalPath,
              )
            } else {
              parentCanonicalPath
            }
          val groupOwnRelevant =
            if (group.field_ref.isNotEmpty()) (relevancyMap[canonicalPath] ?: true) else true
          val isRelevant = parentRelevant && groupOwnRelevant
          val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)
          val resolvedLabel = resolveLabel(group.label, compiled, nodeCtx, activeLanguage)
          val resolvedIntent = group.intent?.let { resolveIntentConfig(it, compiled, nodeCtx) }
          val children =
            materializeViewComponents(
              compiled = compiled,
              components = group.components,
              parentCanonicalPath = canonicalPath,
              parentRelativePath = relPath,
              parentRelevant = isRelevant,
              fieldStates = fieldStates,
              relevancyMap = relevancyMap,
              dynamicRepeatCounts = dynamicRepeatCounts,
              effectiveRecord = effectiveRecord,
              rootEvalContext = rootEvalContext,
              activeLanguage = activeLanguage,
              environment = environment,
            )

          result.add(
            ComponentState.GroupState(
              canonicalPath = canonicalPath,
              groupDef = group,
              isRelevant = isRelevant,
              label = resolvedLabel,
              appearance = group.appearance,
              resolvedIntent = resolvedIntent,
              children = children,
            )
          )
        }
        comp.repeat != null -> {
          val repeat = comp.repeat
          val relPath = compiled.resolveComponentRelativePath(repeat.field_ref, parentRelativePath)
          val canonicalPath =
            RecordMutator.resolveCanonicalPath(
              rawPath = repeat.field_ref,
              rootName = compiled.rootName,
              rootAliases = compiled.rootAliases,
              contextCanonicalPath = parentCanonicalPath,
            )
          val repeatOwnRelevant = relevancyMap[canonicalPath] ?: true
          val isRelevant = parentRelevant && repeatOwnRelevant
          val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)
          val resolvedLabel = resolveLabel(repeat.label, compiled, nodeCtx, activeLanguage)

          val repeatNodes =
            RecordMutator.getRepeatInstances(
              rootNode = effectiveRecord.data_ ?: RecordNode(),
              rootAliases = compiled.rootAliases,
              repeatCanonicalPath = canonicalPath,
            )
          val targetCount = dynamicRepeatCounts[canonicalPath]
          val isCountControlled = repeat.count_expression.isNotBlank()
          val canAdd = isRelevant && !repeat.no_add_remove && !isCountControlled
          val canRemove =
            isRelevant && !repeat.no_add_remove && !isCountControlled && repeatNodes.isNotEmpty()

          val instances = repeatNodes.mapIndexed { idx, _ ->
            val idx1 = idx + 1
            val instancePath = "$canonicalPath[$idx1]"
            val instanceRelevant = isRelevant && (relevancyMap[instancePath] ?: true)
            val instanceCtx =
              positionContextAtPath(
                baseContext = rootEvalContext,
                canonicalPath = instancePath,
                explicitPosition = idx1,
                explicitSize = repeatNodes.size,
              )
            val instanceLabel = resolveLabel(repeat.label, compiled, instanceCtx, activeLanguage)
            val instanceChildren =
              materializeViewComponents(
                compiled = compiled,
                components = repeat.components,
                parentCanonicalPath = instancePath,
                parentRelativePath = relPath,
                parentRelevant = instanceRelevant,
                fieldStates = fieldStates,
                relevancyMap = relevancyMap,
                dynamicRepeatCounts = dynamicRepeatCounts,
                effectiveRecord = effectiveRecord,
                rootEvalContext = rootEvalContext,
                activeLanguage = activeLanguage,
                environment = environment,
              )
            RepeatInstanceState(
              canonicalPath = instancePath,
              repeatIndex = idx1,
              label = instanceLabel,
              isRelevant = instanceRelevant,
              children = instanceChildren,
            )
          }

          result.add(
            ComponentState.RepeatGroupState(
              canonicalPath = canonicalPath,
              repeatDef = repeat,
              isRelevant = isRelevant,
              label = resolvedLabel,
              appearance = repeat.appearance,
              targetCount = targetCount,
              canAddInstance = canAdd,
              canRemoveInstance = canRemove,
              instances = instances,
            )
          )
        }
      }
    }

    return result
  }

  private fun resolveControlOptions(
    control: ControlDef,
    compiled: CompiledForm,
    controlContext: EvaluationContext,
    activeLanguage: String,
    environment: FormEnvironment,
  ): List<ResolvedChoiceOption> {
    val itemset = control.itemset
    if (itemset != null && itemset.instance_id.isNotEmpty()) {
      return evaluateItemsetOptions(
        itemset = itemset,
        compiled = compiled,
        controlContext = controlContext,
        activeLanguage = activeLanguage,
        environment = environment,
      )
    }

    // Static choices from ControlDef.choices
    return control.choices.map { item ->
      val label =
        resolveLabel(item.label, compiled, controlContext, activeLanguage)
          ?: ResolvedLabel(text = item.value_)
      ResolvedChoiceOption(value = item.value_, label = label, properties = item.properties)
    }
  }

  private fun evaluateItemsetOptions(
    itemset: ItemsetDef,
    compiled: CompiledForm,
    controlContext: EvaluationContext,
    activeLanguage: String,
    environment: FormEnvironment,
  ): List<ResolvedChoiceOption> {
    val instanceRoot =
      controlContext.secondaryInstanceProvider.resolveRoot(itemset.instance_id)
        ?: return emptyList()
    val allItems =
      instanceRoot
        .children("root")
        .flatMap { it.children("item") }
        .ifEmpty { instanceRoot.children("item") }

    val filterExpr = compiled.compileExpression(itemset.nodeset_filter)
    val filteredItems =
      if (filterExpr == null) {
        allItems
      } else {
        val total = allItems.size
        allItems.filterIndexed { idx, candidateNode ->
          val itemEvalCtx =
            controlContext.copy(
              contextNode = candidateNode,
              currentQuestionNode = controlContext.contextNode,
              contextPosition = idx + 1,
              contextSize = total,
            )
          filterExpr.evaluateBoolean(itemEvalCtx)
        }
      }

    val options = filteredItems.map { itemNode ->
      val valStr =
        if (itemset.value_ref.isNotEmpty()) {
          itemNode.children(itemset.value_ref).firstOrNull()?.extractValue()?.toXPathString() ?: ""
        } else {
          itemNode.extractValue().toXPathString()
        }

      val rawLabelRef =
        if (itemset.label_ref.isNotEmpty()) {
          if (itemset.label_ref.startsWith("jr:itext(")) {
            val expr = compiled.compileExpression(itemset.label_ref)
            val itemCtx = controlContext.withContextNode(itemNode)
            expr?.evaluateString(itemCtx) ?: valStr
          } else {
            val cellStr =
              itemNode.children(itemset.label_ref).firstOrNull()?.extractValue()?.toXPathString()
                ?: valStr
            // Check if cellStr is a translation text_id key in TranslationCatalog
            lookupLocalizedString(compiled.formDef.model?.translations, activeLanguage, cellStr)
              ?.value_
              ?.takeIf { it.isNotEmpty() } ?: cellStr
          }
        } else {
          valStr
        }

      val localizedEntry =
        lookupLocalizedString(compiled.formDef.model?.translations, activeLanguage, rawLabelRef)
      val resolvedLabel =
        if (localizedEntry != null) {
          ResolvedLabel(
            text = localizedEntry.value_.ifEmpty { rawLabelRef },
            shortText = localizedEntry.short_value.takeIf { it.isNotEmpty() },
            guidanceText = localizedEntry.guidance_value.takeIf { it.isNotEmpty() },
            media = localizedEntry.media,
          )
        } else {
          ResolvedLabel(text = rawLabelRef)
        }

      val props =
        if (itemNode is XPathNode.SecondaryInstanceNode) {
          itemNode.rowAttributes.mapValues { (_, tv) ->
            XPathValue.fromTypedValue(tv).toXPathString()
          }
        } else {
          emptyMap()
        }

      ResolvedChoiceOption(value = valStr, label = resolvedLabel, properties = props)
    }

    if (!itemset.randomize || options.size <= 1) {
      return options
    }

    val seedExpr = compiled.compileExpression(itemset.random_seed_expression)
    val seed: Long? =
      if (seedExpr != null) {
        val num = seedExpr.evaluateNumber(controlContext)
        if (!num.isNaN()) num.roundToLong()
        else seedExpr.evaluateString(controlContext).hashCode().toLong()
      } else {
        environment.randomSeed
      }
    return shuffleOptionsDeterministically(options, seed)
  }

  private fun shuffleOptionsDeterministically(
    options: List<ResolvedChoiceOption>,
    seed: Long?,
  ): List<ResolvedChoiceOption> {
    val list = options.toMutableList()
    val rng = if (seed != null) Random(seed) else Random.Default
    for (i in list.lastIndex downTo 1) {
      val j = rng.nextInt(i + 1)
      val tmp = list[i]
      list[i] = list[j]
      list[j] = tmp
    }
    return list
  }

  private fun evaluateEntityStates(
    compiled: CompiledForm,
    fieldStates: Map<String, FieldState>,
    rootEvalContext: EvaluationContext,
  ): List<EntityState> {
    val declarations = compiled.formDef.model?.entities ?: emptyList()
    if (declarations.isEmpty()) return emptyList()

    // Collect global entity_saveto field values from relevant fields
    val saveToProperties = linkedMapOf<String, TypedValue>()
    for (fs in fieldStates.values) {
      if (!fs.isRelevant || fs.isEmpty) continue
      val saveTo = fs.binding?.entity_saveto?.takeIf { it.isNotEmpty() } ?: continue
      val tv =
        fs.value?.scalar_value ?: XPathValue.fromFieldValue(fs.value).toTypedValue(fs.dataType)
      if (tv != null) {
        saveToProperties[saveTo] = tv
      }
    }

    return declarations.map { decl ->
      val createExpr = compiled.compileExpression(decl.create_condition)
      val updateExpr = compiled.compileExpression(decl.update_condition)
      val shouldCreate =
        when {
          createExpr != null -> createExpr.evaluateBoolean(rootEvalContext)
          updateExpr == null -> true
          else -> false
        }
      val shouldUpdate = updateExpr?.evaluateBoolean(rootEvalContext) ?: false

      val idExpr = compiled.compileExpression(decl.entity_id_expression)
      val entityId = idExpr?.evaluateString(rootEvalContext) ?: ""

      val labelExpr = compiled.compileExpression(decl.label_expression)
      val label = labelExpr?.evaluateString(rootEvalContext) ?: ""

      val sync = decl.sync_metadata
      val baseVer =
        compiled
          .compileExpression(sync?.base_version_expression)
          ?.evaluateNumber(rootEvalContext)
          ?.takeIf { !it.isNaN() }
          ?.roundToInt()
      val trunkVer =
        compiled
          .compileExpression(sync?.trunk_version_expression)
          ?.evaluateNumber(rootEvalContext)
          ?.takeIf { !it.isNaN() }
          ?.roundToInt()
      val branchId =
        compiled
          .compileExpression(sync?.branch_id_expression)
          ?.evaluateString(rootEvalContext)
          ?.takeIf { it.isNotEmpty() }

      val mappedProps = linkedMapOf<String, TypedValue>()
      mappedProps.putAll(saveToProperties)
      for (mapping in decl.property_mappings) {
        if (mapping.entity_property.isEmpty() || mapping.source_field_path.isEmpty()) continue
        val canonicalSource =
          RecordMutator.resolveCanonicalPath(
            rawPath = mapping.source_field_path,
            rootName = compiled.rootName,
            rootAliases = compiled.rootAliases,
          )
        val sourceState = fieldStates[canonicalSource]
        if (sourceState != null && sourceState.isRelevant && !sourceState.isEmpty) {
          val tv =
            sourceState.value?.scalar_value
              ?: XPathValue.fromFieldValue(sourceState.value).toTypedValue(sourceState.dataType)
          if (tv != null) {
            mappedProps[mapping.entity_property] = tv
          }
        }
      }

      EntityState(
        dataset = decl.dataset,
        declaration = decl,
        shouldCreate = shouldCreate,
        shouldUpdate = shouldUpdate,
        entityId = entityId,
        label = label,
        baseVersion = baseVer,
        trunkVersion = trunkVer,
        branchId = branchId,
        properties = mappedProps,
      )
    }
  }

  private fun resolveLabel(
    labelDef: LabelDef?,
    compiled: CompiledForm,
    evalContext: EvaluationContext,
    activeLanguage: String,
  ): ResolvedLabel? {
    if (labelDef == null) return null
    val localized =
      if (labelDef.text_id.isNotEmpty()) {
        lookupLocalizedString(
          catalog = compiled.formDef.model?.translations,
          activeLanguage = activeLanguage,
          textId = labelDef.text_id,
        )
      } else {
        null
      }

    val rawPrimary = localized?.value_?.takeIf { it.isNotEmpty() } ?: labelDef.text
    val rawShort = localized?.short_value?.takeIf { it.isNotEmpty() }
    val rawGuidance = localized?.guidance_value?.takeIf { it.isNotEmpty() }
    val media = localized?.media

    if (
      rawPrimary.isEmpty() &&
        rawShort == null &&
        rawGuidance == null &&
        media == null &&
        labelDef.outputs.isEmpty()
    ) {
      return null
    }

    val interpolatedPrimary = interpolateOutputs(rawPrimary, labelDef, compiled, evalContext)
    val interpolatedShort = rawShort?.let {
      interpolateOutputs(it, labelDef, compiled, evalContext)
    }
    val interpolatedGuidance = rawGuidance?.let {
      interpolateOutputs(it, labelDef, compiled, evalContext)
    }

    return ResolvedLabel(
      text = interpolatedPrimary,
      shortText = interpolatedShort,
      guidanceText = interpolatedGuidance,
      media = media,
    )
  }

  private fun interpolateOutputs(
    template: String,
    labelDef: LabelDef,
    compiled: CompiledForm,
    evalContext: EvaluationContext,
  ): String {
    var result = template
    for ((idx, fragment) in labelDef.outputs.withIndex()) {
      val expr = compiled.compileExpression(fragment.value_expression) ?: continue
      val evaluatedStr = expr.evaluateString(evalContext)
      val pid = fragment.placeholder_id
      if (pid.isNotEmpty()) {
        result = result.replace("\${$pid}", evaluatedStr).replace("{$pid}", evaluatedStr)
        if (pid.startsWith("{") || pid.startsWith("$")) {
          result = result.replace(pid, evaluatedStr)
        }
      }
      result = result.replace("{$idx}", evaluatedStr)
    }
    return result
  }

  private fun resolveBindingMessage(
    rawMessage: String,
    defaultMessage: String,
    compiled: CompiledForm,
    evalContext: EvaluationContext,
    activeLanguage: String,
  ): String {
    val trimmed = rawMessage.trim()
    if (trimmed.isEmpty()) return defaultMessage
    if (trimmed.startsWith("jr:itext(")) {
      val expr = compiled.compileExpression(trimmed)
      val evaluated = expr?.evaluateString(evalContext)
      if (!evaluated.isNullOrEmpty()) return evaluated
    }
    val localized =
      lookupLocalizedString(compiled.formDef.model?.translations, activeLanguage, trimmed)
    return localized?.value_?.takeIf { it.isNotEmpty() } ?: trimmed
  }

  private fun resolveIntentConfig(
    intentConfig: IntentConfig,
    compiled: CompiledForm,
    evalContext: EvaluationContext,
  ): ResolvedIntent {
    val evaluatedParams =
      intentConfig.parameters.mapValues { (_, rawExprOrLiteral) ->
        try {
          val expr = compiled.compileExpression(rawExprOrLiteral)
          expr?.evaluateString(evalContext) ?: rawExprOrLiteral
        } catch (_: Exception) {
          rawExprOrLiteral
        }
      }
    return ResolvedIntent(
      intentUri = intentConfig.intent_uri,
      parameters = evaluatedParams,
      responseMappings = intentConfig.response_mappings,
    )
  }

  private fun lookupLocalizedString(
    catalog: TranslationCatalog?,
    activeLanguage: String,
    textId: String,
  ): LocalizedString? {
    if (catalog == null || catalog.languages.isEmpty() || textId.isEmpty()) return null
    val languages = catalog.languages
    val selectedLang =
      languages.find { it.language.equals(activeLanguage, ignoreCase = true) }
        ?: languages.find { it.is_default }
        ?: languages.first()
    return selectedLang.strings[textId] ?: languages.firstNotNullOfOrNull { it.strings[textId] }
  }

  private fun resolveActiveLanguage(formDef: FormDef, requestedLanguage: String?): String {
    if (!requestedLanguage.isNullOrBlank()) return requestedLanguage
    if (formDef.default_language.isNotBlank()) return formDef.default_language
    val catalog = formDef.model?.translations
    return catalog?.languages?.find { it.is_default }?.language
      ?: catalog?.languages?.firstOrNull()?.language
      ?: "default"
  }

  private fun createEvaluationContext(
    compiled: CompiledForm,
    recordInstance: RecordInstance,
    activeLanguage: String,
    secondaryProvider: SecondaryInstanceProvider,
    environment: FormEnvironment,
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
        randomSeed = environment.randomSeed,
      )
    return if (contextCanonicalPath.isNullOrBlank()) {
      base
    } else {
      positionContextAtPath(base, contextCanonicalPath)
    }
  }

  private fun positionContextAtPath(
    baseContext: EvaluationContext,
    canonicalPath: String,
    explicitPosition: Int? = null,
    explicitSize: Int? = null,
  ): EvaluationContext {
    val segments = RecordMutator.parseSegments(canonicalPath)
    val steps =
      if (
        segments.isNotEmpty() &&
          segments.first().name in
            RecordMutator.resolveRootAliases(baseContext.formDef ?: FormDef())
      ) {
        segments.drop(1)
      } else {
        segments
      }

    var currentNode: XPathNode =
      if (baseContext.rootNode is XPathNode.DocumentRootNode) {
        baseContext.rootNode.instanceElementNode
      } else {
        baseContext.rootNode
      }

    for (seg in steps) {
      val candidates = currentNode.children(seg.name)
      if (candidates.isEmpty()) break
      val targetIdx = (seg.repeatIndex ?: 1) - 1
      currentNode = candidates.getOrElse(targetIdx) { candidates.first() }
    }

    return baseContext.copy(
      contextNode = currentNode,
      currentQuestionNode = currentNode,
      contextPosition = explicitPosition ?: currentNode.repeatIndex,
      contextSize = explicitSize ?: currentNode.siblingRepeatCount,
    )
  }

  private fun expandConcretePathsForRelativePath(
    rootNode: RecordNode,
    rootName: String,
    relativePath: String,
  ): List<String> {
    if (relativePath.isEmpty()) return listOf("/$rootName")
    val segments = relativePath.split('/').filter { it.isNotEmpty() }
    val results = mutableListOf<String>()

    fun expand(currentNode: RecordNode?, currentPrefix: String, segIndex: Int) {
      if (segIndex >= segments.size) {
        results.add(currentPrefix)
        return
      }
      val segName = segments[segIndex]
      val fv = currentNode?.fields?.get(segName)
      when {
        fv?.repeat_value != null -> {
          val nodes = fv.repeat_value.nodes
          for ((idx, itemNode) in nodes.withIndex()) {
            val idx1 = idx + 1
            expand(itemNode, "$currentPrefix/$segName[$idx1]", segIndex + 1)
          }
        }
        fv?.node_value != null -> {
          expand(fv.node_value, "$currentPrefix/$segName", segIndex + 1)
        }
        else -> {
          expand(null, "$currentPrefix/$segName", segIndex + 1)
        }
      }
    }

    expand(rootNode, "/$rootName", 0)
    return results
  }

  private fun collectRangeConfigs(
    compiled: CompiledForm
  ): Map<String, groundplatform.v2.forms.RangeConfig> {
    val map = mutableMapOf<String, groundplatform.v2.forms.RangeConfig>()
    fun visit(components: List<ViewComponent>, parentPath: String) {
      for (comp in components) {
        when {
          comp.control != null -> {
            val c = comp.control
            val rel = compiled.resolveComponentRelativePath(c.field_ref, parentPath)
            if (c.range_config != null && rel.isNotEmpty()) {
              map[rel] = c.range_config
            }
          }
          comp.group != null -> {
            val g = comp.group
            visit(g.components, compiled.resolveComponentRelativePath(g.field_ref, parentPath))
          }
          comp.repeat != null -> {
            val r = comp.repeat
            visit(r.components, compiled.resolveComponentRelativePath(r.field_ref, parentPath))
          }
        }
      }
    }
    visit(compiled.formDef.view?.components ?: emptyList(), "")
    return map
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
