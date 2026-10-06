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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Where collectors can open a Form: the mobile app, the web dashboard, both, or neither. Set by the
 * survey designer with the two per-platform **Available on** switches in the Form editor's Form
 * settings; Forms designed in the editor start as [MOBILE].
 */
enum class FormAvailability(val includesMobile: Boolean, val includesWeb: Boolean) {
  /** Collectors can open the Form in the mobile app only. */
  MOBILE(includesMobile = true, includesWeb = false),
  /** Collectors can open the Form in the web dashboard only. */
  WEB(includesMobile = false, includesWeb = true),
  /** Collectors can open the Form in the mobile app and the web dashboard. */
  BOTH(includesMobile = true, includesWeb = true),
  /** Collectors can't open the Form anywhere; it's hidden from every entry point. */
  NONE(includesMobile = false, includesWeb = false);

  /** This availability with the mobile app switched on or off. */
  fun withMobile(enabled: Boolean): FormAvailability = of(mobile = enabled, web = includesWeb)

  /** This availability with the web dashboard switched on or off. */
  fun withWeb(enabled: Boolean): FormAvailability = of(mobile = includesMobile, web = enabled)

  companion object {
    /** The availability matching the two per-platform toggles. */
    fun of(mobile: Boolean, web: Boolean): FormAvailability =
      when {
        mobile && web -> BOTH
        mobile -> MOBILE
        web -> WEB
        else -> NONE
      }
  }
}

/** Represents a hierarchical Form (`FormDef` + `FormLaunchConfig`) in the active survey. */
data class FormPreviewItem(
  val id: String,
  val title: String,
  val description: String,
  val version: String,
  val targetDatasetId: String,
  val targetDatasetName: String,
  val questionCount: Int,
  val ctaLabel: String,
  val requiresEntity: Boolean = targetDatasetId.isNotBlank(),
  /** Platforms the Form can be collected on. */
  val availability: FormAvailability = FormAvailability.BOTH,
) {

  /**
   * Singular domain label for the target dataset required by this form (e.g. `"Coffee Parcel"`).
   */
  val targetSingularTypeLabel: String
    get() =
      when (targetDatasetId) {
        "coffee_parcels" -> "Coffee Parcel"
        "shade_monitoring_plots" -> "Shade Tree Monitoring Plot"
        "washing_stations" -> "Cooperative Washing Station"
        else -> targetDatasetName.removeSuffix("s").ifBlank { "Location" }
      }
}

/** Grouping of [SubmissionPreviewItem]s under a [FormPreviewItem] in submission lists. */
data class FormSubmissionsGroup(
  val form: FormPreviewItem,
  val submissions: List<SubmissionPreviewItem>,
) {
  val formTitle: String
    get() = form.title
}
