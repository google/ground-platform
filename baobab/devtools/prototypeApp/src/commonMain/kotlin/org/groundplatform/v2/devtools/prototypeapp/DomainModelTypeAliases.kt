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
package org.groundplatform.v2.devtools.prototypeapp

import org.groundplatform.v2.devtools.prototypeapp.domain.model.ClusterMarkerSymbolGroup as DomainClusterMarkerSymbolGroup
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LANGUAGE_OPTIONS as DOMAIN_LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem as DomainMapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem as DomainSubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus as DomainSyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.WEBSITE_URL as DOMAIN_WEBSITE_URL
import org.groundplatform.v2.devtools.prototypeapp.domain.model.deriveEntitySyncStatus as domainDeriveEntitySyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatHexColorCss as domainFormatHexColorCss
import org.groundplatform.v2.devtools.prototypeapp.domain.model.groupClusterFeaturesByMarkerSymbol as domainGroupClusterFeaturesByMarkerSymbol
import org.groundplatform.v2.devtools.prototypeapp.domain.model.inferTargetZoomForPlace as domainInferTargetZoomForPlace
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parseHexColorOrDefault as domainParseHexColorOrDefault
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates as domainParsePlaceCoordinates

typealias MapScaleBarSpec = org.groundplatform.v2.devtools.prototypeapp.domain.model.MapScaleBarSpec

typealias AppScreen = org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen

typealias PrototypeScreen = org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen

typealias MainSurveyViewMode =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode

typealias EntityDetailsPane =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityDetailsPane

typealias ListFilterTab = org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab

typealias LocationLockState =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState

typealias SurveyPlaceItem = org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem

typealias DeviceOrientation =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation

typealias DeviceFormFactor =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor

typealias MainDrawerSubView =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView

typealias UploadStatusFilter =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter

typealias MutationSyncState =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState

typealias MutationOperationKind =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationOperationKind

typealias MutationLogItem = org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem

typealias MeasurementUnitSystem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem

typealias LanguageOption = org.groundplatform.v2.devtools.prototypeapp.domain.model.LanguageOption

typealias GroundLanguageOption =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.LanguageOption

typealias UserSettings = org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

typealias DeviceStorageInfo =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceStorageInfo

typealias MapThumbnailTheme =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme

typealias SurveyPreviewItem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

typealias LayerSourceType = org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType

typealias BasemapType = org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType

typealias OfflineBasemapStyle =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle

typealias MapLayerItem = org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem

typealias SubmissionGeometryPolygon =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon

typealias SubmissionFieldEntry =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry

typealias SyncStatus = org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus

typealias SubmissionPreviewItem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem

typealias GeospatialEntityItem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem

typealias MapFeatureKind = org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureKind

typealias MapClusterFeatureItem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem

typealias ClusterMarkerSymbolGroup =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.ClusterMarkerSymbolGroup

typealias MapFeatureCluster =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster

typealias FormPreviewItem = org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem

typealias EntityDatasetFeaturesGroup =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityDatasetFeaturesGroup

typealias FormSubmissionsGroup =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.FormSubmissionsGroup

typealias SharedPdfSheetState =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.SharedPdfSheetState

typealias NavigationTargetKind =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind

typealias StraightLineVector =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineVector

typealias StraightLineNavigationState =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineNavigationState

typealias OfflineTilePackageItem =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem

typealias DownloadSurveyEntryOrigin =
  org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin

typealias PrototypeAppDataStore =
  org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore

typealias AppUiState = org.groundplatform.v2.devtools.prototypeapp.ui.state.AppUiState

typealias MapViewportUiState =
  org.groundplatform.v2.devtools.prototypeapp.ui.state.MapViewportUiState

typealias FormCollectionUiState =
  org.groundplatform.v2.devtools.prototypeapp.ui.state.FormCollectionUiState

typealias PrototypeWorkbenchUiState =
  org.groundplatform.v2.devtools.prototypeapp.ui.state.PrototypeWorkbenchUiState

typealias PrototypeUiState = org.groundplatform.v2.devtools.prototypeapp.ui.state.AppUiState

typealias SurveyAppViewModel =
  org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyAppViewModel

typealias PrototypeAppViewModel =
  org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyAppViewModel

const val WEBSITE_URL = DOMAIN_WEBSITE_URL

const val GROUND_WEBSITE_URL = DOMAIN_WEBSITE_URL

val LANGUAGE_OPTIONS = DOMAIN_LANGUAGE_OPTIONS

val GROUND_LANGUAGE_OPTIONS = DOMAIN_LANGUAGE_OPTIONS

fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? =
  domainParsePlaceCoordinates(coordinatesLabel)

internal fun inferTargetZoomForPlace(
  categoryLabel: String,
  bboxMinLng: Double? = null,
  bboxMinLat: Double? = null,
  bboxMaxLng: Double? = null,
  bboxMaxLat: Double? = null,
  fallbackZoomDelta: Float = 0.75f,
): Float =
  domainInferTargetZoomForPlace(
    categoryLabel = categoryLabel,
    bboxMinLng = bboxMinLng,
    bboxMinLat = bboxMinLat,
    bboxMaxLng = bboxMaxLng,
    bboxMaxLat = bboxMaxLat,
    fallbackZoomDelta = fallbackZoomDelta,
  )

fun parseHexColorOrDefault(cssColor: String?, fallbackHex: Long): Long =
  domainParseHexColorOrDefault(cssColor, fallbackHex)

fun formatHexColorCss(colorHex: Long): String = domainFormatHexColorCss(colorHex)

fun deriveEntitySyncStatus(
  submissions: List<DomainSubmissionPreviewItem>,
  fallback: DomainSyncStatus = DomainSyncStatus.SYNCED,
): DomainSyncStatus = domainDeriveEntitySyncStatus(submissions, fallback)

internal fun groupClusterFeaturesByMarkerSymbol(
  features: List<DomainMapClusterFeatureItem>
): List<DomainClusterMarkerSymbolGroup> = domainGroupClusterFeaturesByMarkerSymbol(features)
