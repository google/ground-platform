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
package org.groundplatform.v2.app

import org.groundplatform.v2.app.domain.model.ClusterMarkerSymbolGroup as DomainClusterMarkerSymbolGroup
import org.groundplatform.v2.app.domain.model.LANGUAGE_OPTIONS as DOMAIN_LANGUAGE_OPTIONS
import org.groundplatform.v2.app.domain.model.MapClusterFeatureItem as DomainMapClusterFeatureItem
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem as DomainSubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SyncStatus as DomainSyncStatus
import org.groundplatform.v2.app.domain.model.WEBSITE_URL as DOMAIN_WEBSITE_URL
import org.groundplatform.v2.app.domain.model.deriveEntitySyncStatus as domainDeriveEntitySyncStatus
import org.groundplatform.v2.app.domain.model.formatHexColorCss as domainFormatHexColorCss
import org.groundplatform.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol as domainGroupClusterFeaturesByMarkerSymbol
import org.groundplatform.v2.app.domain.model.inferTargetZoomForPlace as domainInferTargetZoomForPlace
import org.groundplatform.v2.app.domain.model.parseHexColorOrDefault as domainParseHexColorOrDefault
import org.groundplatform.v2.app.domain.model.parsePlaceCoordinates as domainParsePlaceCoordinates

typealias MapScaleBarSpec = org.groundplatform.v2.app.domain.model.MapScaleBarSpec

typealias AppScreen = org.groundplatform.v2.app.domain.model.AppScreen

typealias MainSurveyViewMode = org.groundplatform.v2.app.domain.model.MainSurveyViewMode

typealias EntityDetailsPane = org.groundplatform.v2.app.domain.model.EntityDetailsPane

typealias ListFilterTab = org.groundplatform.v2.app.domain.model.ListFilterTab

typealias LocationLockState = org.groundplatform.v2.app.domain.model.LocationLockState

typealias SurveyPlaceItem = org.groundplatform.v2.app.domain.model.SurveyPlaceItem

typealias DeviceOrientation = org.groundplatform.v2.app.domain.model.DeviceOrientation

typealias DeviceFormFactor = org.groundplatform.v2.app.domain.model.DeviceFormFactor

typealias MainDrawerSubView = org.groundplatform.v2.app.domain.model.MainDrawerSubView

typealias UploadStatusFilter = org.groundplatform.v2.app.domain.model.UploadStatusFilter

typealias MutationSyncState = org.groundplatform.v2.app.domain.model.MutationSyncState

typealias MutationOperationKind = org.groundplatform.v2.app.domain.model.MutationOperationKind

typealias MutationLogItem = org.groundplatform.v2.app.domain.model.MutationLogItem

typealias MeasurementUnitSystem = org.groundplatform.v2.app.domain.model.MeasurementUnitSystem

typealias LanguageOption = org.groundplatform.v2.app.domain.model.LanguageOption

typealias UserSettings = org.groundplatform.v2.app.domain.model.UserSettings

typealias DeviceStorageInfo = org.groundplatform.v2.app.domain.model.DeviceStorageInfo

typealias MapThumbnailTheme = org.groundplatform.v2.app.domain.model.MapThumbnailTheme

typealias SurveyPreviewItem = org.groundplatform.v2.app.domain.model.SurveyPreviewItem

typealias Organization = org.groundplatform.v2.app.domain.model.Organization

typealias ImagerySource = org.groundplatform.v2.app.domain.model.ImagerySource

typealias ImagerySourceType = org.groundplatform.v2.app.domain.model.ImagerySourceType

typealias OrganizationMember = org.groundplatform.v2.app.domain.model.OrganizationMember

typealias OrganizationRole = org.groundplatform.v2.app.domain.model.OrganizationRole

typealias MembershipStatus = org.groundplatform.v2.app.domain.model.MembershipStatus

typealias LayerSourceType = org.groundplatform.v2.app.domain.model.LayerSourceType

typealias BasemapType = org.groundplatform.v2.app.domain.model.BasemapType

typealias OfflineBasemapStyle = org.groundplatform.v2.app.domain.model.OfflineBasemapStyle

typealias MapLayerItem = org.groundplatform.v2.app.domain.model.MapLayerItem

typealias SubmissionGeometryPolygon =
  org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon

typealias SubmissionFieldEntry = org.groundplatform.v2.app.domain.model.SubmissionFieldEntry

typealias SyncStatus = org.groundplatform.v2.app.domain.model.SyncStatus

typealias SubmissionPreviewItem = org.groundplatform.v2.app.domain.model.SubmissionPreviewItem

typealias GeospatialEntityItem = org.groundplatform.v2.app.domain.model.GeospatialEntityItem

typealias MapFeatureKind = org.groundplatform.v2.app.domain.model.MapFeatureKind

typealias MapClusterFeatureItem = org.groundplatform.v2.app.domain.model.MapClusterFeatureItem

typealias ClusterMarkerSymbolGroup = org.groundplatform.v2.app.domain.model.ClusterMarkerSymbolGroup

typealias MapFeatureCluster = org.groundplatform.v2.app.domain.model.MapFeatureCluster

typealias FormPreviewItem = org.groundplatform.v2.app.domain.model.FormPreviewItem

typealias EntityDatasetFeaturesGroup =
  org.groundplatform.v2.app.domain.model.EntityDatasetFeaturesGroup

typealias FormSubmissionsGroup = org.groundplatform.v2.app.domain.model.FormSubmissionsGroup

typealias SharedPdfSheetState = org.groundplatform.v2.app.domain.model.SharedPdfSheetState

typealias NavigationTargetKind = org.groundplatform.v2.app.domain.model.NavigationTargetKind

typealias StraightLineVector = org.groundplatform.v2.app.domain.model.StraightLineVector

typealias StraightLineNavigationState =
  org.groundplatform.v2.app.domain.model.StraightLineNavigationState

typealias OfflineTilePackageItem = org.groundplatform.v2.app.domain.model.OfflineTilePackageItem

typealias DownloadSurveyEntryOrigin =
  org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin

typealias AppDataHolder = org.groundplatform.v2.app.di.AppDataHolder

typealias EntityGeometryKind = org.groundplatform.v2.app.domain.model.EntityGeometryKind

const val WEBSITE_URL = DOMAIN_WEBSITE_URL

val LANGUAGE_OPTIONS = DOMAIN_LANGUAGE_OPTIONS

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
