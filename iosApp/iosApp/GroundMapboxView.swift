// Copyright 2026 The Ground Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import GroundMobile
import MapboxMaps
import UIKit
import os

private let log = Logger(subsystem: "org.groundplatform.v2", category: "GroundMap")

/// Creates the Mapbox maps behind `GroundMap` (shared/map). Registered in `iOSApp`.
///
/// Returns `nil`, so `GroundMap` falls back to its preview renderer, when the app has no Mapbox
/// access token (`MBXAccessToken` in Info.plist, set from the `MAPBOX_ACCESS_TOKEN` build
/// setting).
final class GroundMapboxViewFactory: NSObject, MapboxIosViewFactory {
  func create() -> (any MapboxIosView)? {
    let token = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String ?? ""
    guard !token.isEmpty, !token.hasPrefix("$(") else { return nil }
    return GroundMapboxView()
  }
}

/// A display-only Mapbox `MapView`. The Kotlin renderer translates `MapContent` into Mapbox Style
/// Specification JSON and handles gestures in Compose, so this class only passes JSON through to
/// the SDK. Failed style calls are logged, not thrown.
final class GroundMapboxView: NSObject, MapboxIosView {
  private let mapView: MapView
  private var queries: [Cancelable] = []

  override init() {
    // No initial style: the renderer loads the basemap's style right away.
    mapView = MapView(frame: .zero, mapInitOptions: MapInitOptions(styleURI: nil))
    super.init()
    // Compose draws the scale bar it needs and never rotates the map. The logo and the attribution
    // button (with the telemetry opt-out the Mapbox terms require) stay.
    mapView.ornaments.options.compass.visibility = .hidden
    mapView.ornaments.options.scaleBar.visibility = .hidden
    mapView.gestures.options.panEnabled = false
    mapView.gestures.options.pinchEnabled = false
    mapView.gestures.options.rotateEnabled = false
    mapView.gestures.options.pitchEnabled = false
    mapView.gestures.options.doubleTapToZoomInEnabled = false
    mapView.gestures.options.doubleTouchToZoomOutEnabled = false
    mapView.gestures.options.quickZoomEnabled = false
  }

  var view: UIView { mapView }

  func loadStyle(style: String, onLoaded: @escaping () -> Void) {
    let completion: (Error?) -> Void = { error in
      if let error {
        log.error("Failed to load style: \(error.localizedDescription)")
        return
      }
      onLoaded()
    }
    if style.hasPrefix("{") {
      mapView.mapboxMap.loadStyle(style, completion: completion)
    } else if let uri = StyleURI(rawValue: style) {
      mapView.mapboxMap.loadStyle(uri, completion: completion)
    } else {
      log.error("Invalid style URL: \(style)")
    }
  }

  func addSource(id: String, json: String) {
    guard let properties = object(json) else { return }
    attempt("add source \(id)") { try mapView.mapboxMap.addSource(withId: id, properties: properties) }
  }

  func setSourceData(id: String, geoJson: String) {
    guard let data = object(geoJson) else { return }
    attempt("set source data \(id)") {
      try mapView.mapboxMap.setSourceProperty(for: id, property: "data", value: data)
    }
  }

  func removeSource(id: String) {
    guard mapView.mapboxMap.sourceExists(withId: id) else { return }
    attempt("remove source \(id)") { try mapView.mapboxMap.removeSource(withId: id) }
  }

  func addLayer(json: String, belowLayerId: String?) {
    guard let properties = object(json) else { return }
    let position = belowLayerId.map { LayerPosition.below($0) }
    attempt("add layer") {
      try mapView.mapboxMap.addLayer(with: properties, layerPosition: position)
    }
  }

  func removeLayer(id: String) {
    attempt("remove layer \(id)") { try mapView.mapboxMap.removeLayer(withId: id) }
  }

  func hasLayer(id: String) -> Bool {
    mapView.mapboxMap.layerExists(withId: id)
  }

  func addImage(id: String, png: Data, scale: Double) {
    guard let image = UIImage(data: png, scale: CGFloat(scale)) else {
      log.error("Invalid image \(id)")
      return
    }
    attempt("add image \(id)") { try mapView.mapboxMap.addImage(image, id: id) }
  }

  func removeImage(id: String) {
    guard mapView.mapboxMap.imageExists(withId: id) else { return }
    attempt("remove image \(id)") { try mapView.mapboxMap.removeImage(withId: id) }
  }

  func setCamera(latitude: Double, longitude: Double, zoom: Double, bearing: Double, pitch: Double) {
    mapView.mapboxMap.setCamera(
      to: CameraOptions(
        center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
        zoom: CGFloat(zoom),
        bearing: bearing,
        pitch: CGFloat(pitch)
      )
    )
  }

  func queryFeature(
    x: Double,
    y: Double,
    radius: Double,
    layerIds: [String],
    idProperty: String,
    onResult: @escaping (String?, String?) -> Void
  ) {
    let box = CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)
    let options = RenderedQueryOptions(layerIds: layerIds, filter: nil)
    let query = mapView.mapboxMap.queryRenderedFeatures(with: box, options: options) { result in
      // Results are ordered topmost first.
      if case .success(let features) = result {
        for rendered in features {
          guard let layerId = rendered.layers.first,
            let value = rendered.queriedFeature.feature.properties?[idProperty],
            case .string(let featureId)? = value
          else { continue }
          onResult(layerId, featureId)
          return
        }
      }
      onResult(nil, nil)
    }
    queries.append(query)
    if queries.count > 8 { queries.removeFirst() }
  }

  func tapOrnament(x: Double, y: Double) -> Bool {
    let button = mapView.ornaments.attributionButton
    guard !button.isHidden, button.window != nil else { return false }
    let target = button.convert(button.bounds, to: mapView).insetBy(dx: -8, dy: -8)
    guard target.contains(CGPoint(x: x, y: y)) else { return false }
    button.sendActions(for: .touchUpInside)
    return true
  }

  func destroy() {
    queries.forEach { $0.cancel() }
    queries.removeAll()
    mapView.removeFromSuperview()
  }

  private func object(_ json: String) -> [String: Any]? {
    let parsed = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
    if parsed == nil { log.error("Invalid style JSON") }
    return parsed
  }

  private func attempt(_ what: String, _ body: () throws -> Void) {
    do {
      try body()
    } catch {
      log.error("Failed to \(what): \(error.localizedDescription)")
    }
  }
}
