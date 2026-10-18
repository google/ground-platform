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
package org.groundplatform.v2.map

import platform.Foundation.NSData
import platform.UIKit.UIView

/**
 * Where the iOS app plugs in its Mapbox map.
 *
 * The Mapbox Maps SDK for iOS is a Swift API, which Kotlin/Native can't call directly, so the app
 * implements [MapboxIosViewFactory] in Swift and registers it at launch:
 * ```swift
 * GroundMapIos.shared.viewFactory = GroundMapboxViewFactory()
 * ```
 *
 * Without a factory, or when it returns `nil` (e.g. no access token), [GroundMap] falls back to
 * [PreviewMap].
 */
object GroundMapIos {
  var viewFactory: MapboxIosViewFactory? = null
}

/** Creates one native map per [GroundMap] on screen. */
interface MapboxIosViewFactory {
  /** A new map view, or `null` if Mapbox can't be used, e.g. without an access token. */
  fun create(): MapboxIosView?
}

/**
 * A display-only Mapbox `MapView`, driven by the iOS renderer.
 *
 * The renderer does all translation to the Mapbox Style Specification in Kotlin, so every call
 * passes style JSON straight through to the SDK. Gestures run in Compose: the view never needs to
 * handle touches. All calls happen on the main thread. Implementations log failed style calls
 * rather than throwing; one bad layer shouldn't stop the rest of the map from rendering.
 */
interface MapboxIosView {
  /** The `MapView`, hosted by Compose in a `UIKitView`. */
  val view: UIView

  /**
   * Replaces the style with [style], a style URL (e.g. `mapbox://styles/mapbox/outdoors-v12`) or a
   * style JSON document (starting with `{`), and calls [onLoaded] once it has loaded. Sources,
   * layers, and images from the previous style are gone afterwards.
   */
  fun loadStyle(style: String, onLoaded: () -> Unit)

  /** Adds a source; [json] is a Style Specification source object. */
  fun addSource(id: String, json: String)

  /** Replaces the `data` of GeoJSON source [id] with [geoJson]. */
  fun setSourceData(id: String, geoJson: String)

  fun removeSource(id: String)

  /**
   * Adds a layer; [json] is a Style Specification layer object. It goes below [belowLayerId] when
   * non-null, and on top otherwise.
   */
  fun addLayer(json: String, belowLayerId: String?)

  fun removeLayer(id: String)

  fun hasLayer(id: String): Boolean

  /** Adds a style image from [png] pixels at [scale] pixels per point. */
  fun addImage(id: String, png: NSData, scale: Double)

  fun removeImage(id: String)

  /** Jumps the camera; the same values as [CameraPosition], since Mapbox iOS uses 512 pt tiles. */
  fun setCamera(latitude: Double, longitude: Double, zoom: Double, bearing: Double, pitch: Double)

  /**
   * Finds the topmost rendered feature within [radius] points of ([x], [y]) in [layerIds], and
   * calls [onResult] with its layer id and the value of its [idProperty], or two `null`s.
   */
  fun queryFeature(
    x: Double,
    y: Double,
    radius: Double,
    layerIds: List<String>,
    idProperty: String,
    onResult: (layerId: String?, featureId: String?) -> Unit,
  )

  /**
   * Taps the map's own controls (the attribution button) at ([x], [y]) in points, which the Compose
   * gesture layer above them would otherwise swallow. Returns whether a control took the tap.
   */
  fun tapOrnament(x: Double, y: Double): Boolean

  /** Releases the map; no other calls follow. */
  fun destroy()
}
