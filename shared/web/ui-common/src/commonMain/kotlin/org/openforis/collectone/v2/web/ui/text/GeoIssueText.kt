/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.geo.io.GeoIssue
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.geo_issue_coordinates_out_of_range
import org.openforis.collectone.v2.web.resources.geo_issue_feature_collection_without_features
import org.openforis.collectone.v2.web.resources.geo_issue_feature_without_geometry
import org.openforis.collectone.v2.web.resources.geo_issue_geojson_not_an_object
import org.openforis.collectone.v2.web.resources.geo_issue_geojson_without_type
import org.openforis.collectone.v2.web.resources.geo_issue_geometry_type_unknown
import org.openforis.collectone.v2.web.resources.geo_issue_holes_removed
import org.openforis.collectone.v2.web.resources.geo_issue_invalid_coordinates
import org.openforis.collectone.v2.web.resources.geo_issue_invalid_feature
import org.openforis.collectone.v2.web.resources.geo_issue_invalid_geometry
import org.openforis.collectone.v2.web.resources.geo_issue_invalid_kml
import org.openforis.collectone.v2.web.resources.geo_issue_may_cross_antimeridian
import org.openforis.collectone.v2.web.resources.geo_issue_no_geometry
import org.openforis.collectone.v2.web.resources.geo_issue_outline_closed
import org.openforis.collectone.v2.web.resources.geo_issue_self_intersection
import org.openforis.collectone.v2.web.resources.geo_issue_too_few_corners
import org.openforis.collectone.v2.web.resources.geo_issue_unreadable_geojson
import org.openforis.collectone.v2.web.resources.geo_issue_unsupported_file_type
import org.openforis.collectone.v2.web.resources.geo_issue_unsupported_geometry_type

/**
 * [issue] in the user's language, e.g. "Feature 3 has no geometry and was skipped." Features are
 * numbered from 1, as organizers count them in their GIS tool.
 */
fun geoIssueText(issue: GeoIssue): UiText =
  when (issue) {
    GeoIssue.UnsupportedFileType -> Res.string.geo_issue_unsupported_file_type.toUiText()
    GeoIssue.UnreadableGeoJson -> Res.string.geo_issue_unreadable_geojson.toUiText()
    GeoIssue.GeoJsonNotAnObject -> Res.string.geo_issue_geojson_not_an_object.toUiText()
    GeoIssue.GeoJsonWithoutType -> Res.string.geo_issue_geojson_without_type.toUiText()
    GeoIssue.InvalidKml -> Res.string.geo_issue_invalid_kml.toUiText()
    GeoIssue.NoGeometry -> Res.string.geo_issue_no_geometry.toUiText()
    GeoIssue.FeatureCollectionWithoutFeatures ->
      Res.string.geo_issue_feature_collection_without_features.toUiText()
    is GeoIssue.HolesRemoved ->
      Res.plurals.geo_issue_holes_removed.toUiText(issue.count, issue.count)
    is GeoIssue.InvalidFeature -> Res.string.geo_issue_invalid_feature.forFeature(issue)
    is GeoIssue.FeatureWithoutGeometry ->
      Res.string.geo_issue_feature_without_geometry.forFeature(issue)
    is GeoIssue.InvalidGeometry -> Res.string.geo_issue_invalid_geometry.forFeature(issue)
    is GeoIssue.UnsupportedGeometryType ->
      Res.string.geo_issue_unsupported_geometry_type.toUiText(
        issue.featureNumber,
        issue.type ?: Res.string.geo_issue_geometry_type_unknown,
      )
    is GeoIssue.InvalidCoordinates -> Res.string.geo_issue_invalid_coordinates.forFeature(issue)
    is GeoIssue.CoordinatesOutOfRange ->
      Res.string.geo_issue_coordinates_out_of_range.forFeature(issue)
    is GeoIssue.TooFewCorners -> Res.string.geo_issue_too_few_corners.forFeature(issue)
    is GeoIssue.OutlineClosed -> Res.string.geo_issue_outline_closed.forFeature(issue)
    is GeoIssue.SelfIntersection -> Res.string.geo_issue_self_intersection.forFeature(issue)
    is GeoIssue.MayCrossAntimeridian ->
      Res.string.geo_issue_may_cross_antimeridian.forFeature(issue)
  }

/** The 1-based number of the feature, as shown to organizers. */
private val GeoIssue.FeatureIssue.featureNumber: Int
  get() = featureIndex + 1

private fun StringResource.forFeature(issue: GeoIssue.FeatureIssue): UiText =
  toUiText(issue.featureNumber)
