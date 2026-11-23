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
@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package org.openforis.collectone.v2.core.geo.io

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.roundToLong
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.sampling.SampleDesign

/**
 * Shared OGC KML 2.2 writer used by both the Web Console exporter (`KmlSurveyExporter`) and the
 * backend `GET /kml/surveys/{surveyId}.kml` endpoint (`KmlExportWebService`).
 *
 * Organizes map features into one KML `<Folder>` per map layer, each paired with a layer-specific
 * `<Style>` whose `<BalloonStyle>` (`<gx:displayMode>panel</gx:displayMode>`) embeds Collect One's
 * standalone Kotlin/Wasm web form runner (`balloon-form.html`).
 */
object KmlBalloonWriter {
  const val KML_MIME = "application/vnd.google-earth.kml+xml;charset=utf-8"
  const val DEFAULT_BALLOON_PATH = "/balloon/balloon-form.html"
  const val DEFAULT_LAYER_COLOR_HEX: Long = 0xFF2E7D32L
  const val STATUS_IN_PROGRESS_COLOR_HEX: Long = 0xFFF9AB00L
  const val STATUS_COMPLETED_COLOR_HEX: Long = 0xFF00C853L
  const val STATUS_NEEDS_REVIEW_COLOR_HEX: Long = 0xFFF37C22L
  const val STATUS_FLAGGED_COLOR_HEX: Long = 0xFFD13135L
  const val DEFAULT_REFRESH_INTERVAL_SECONDS: Int = 15

  private const val DEFAULT_ICON_HREF = "https://maps.google.com/mapfiles/kml/paddle/wht-circle.png"
  private const val IN_PROGRESS_ICON_HREF =
    "https://maps.google.com/mapfiles/kml/paddle/wht-diamond.png"
  private const val COMPLETED_ICON_HREF =
    "https://maps.google.com/mapfiles/kml/paddle/wht-stars.png"
  private const val NEEDS_REVIEW_ICON_HREF =
    "https://maps.google.com/mapfiles/kml/paddle/wht-square.png"
  private const val FLAGGED_ICON_HREF = "https://maps.google.com/mapfiles/kml/paddle/wht-square.png"
  private val PRESENTATION_PROPERTY_KEYS =
    setOf("marker-symbol", "marker-color", "stroke", "fill", "status")
  private val LEADING_STATUS_PREFIX_REGEX = Regex("""^[○◐✓!✕]\s+""")

  /** A web-enabled [FormDef] embedded in a KML layer's `<BalloonStyle>`. */
  data class EmbeddedForm(
    val id: String,
    val title: String,
    val version: String,
    val formDef: FormDef,
  )

  /** Host and session credentials passed to the embedded `balloon-form.html` runner. */
  data class SessionConfig(
    val balloonAppUrl: String = "",
    val serviceUrl: String = "",
    val authToken: String? = null,
    val devRole: String? = null,
    val collectorName: String = "",
    val collectorEmail: String = "",
  )

  /** One placemark inside a map-layer `<Folder>`. */
  data class PlacemarkSpec(
    val id: String,
    val title: String,
    val geoId: String = "",
    val status: String = "",
    val markerSymbol: String = "",
    val geometry: Geometry? = null,
    val properties: Map<String, String> = emptyMap(),
    val subPlots: List<GeoCoord> = emptyList(),
  )

  /** One map-layer `<Folder>` in a survey KML `<Document>`. */
  data class LayerFolderSpec(
    val layerId: String,
    val datasetId: String,
    val name: String,
    val description: String = "",
    val colorHex: Long = DEFAULT_LAYER_COLOR_HEX,
    val isVisible: Boolean = true,
    val forms: List<EmbeddedForm> = emptyList(),
    val placemarks: List<PlacemarkSpec> = emptyList(),
    val sampleDesign: SampleDesign? = null,
  )

  /**
   * Writes a complete OGC KML 2.2 `<Document>` with one `<Folder>` per entry in [layers].
   *
   * When [flattenSingleLayer] is `true` and [layers] has exactly one entry, its placemarks are
   * emitted directly inside `<Document>` (named after that layer) instead of wrapping them in a
   * redundant nested `<Folder>`.
   */
  fun writeDocument(
    surveyId: String,
    surveyTitle: String,
    documentName: String,
    documentDescription: String = "",
    layers: List<LayerFolderSpec>,
    session: SessionConfig = SessionConfig(),
    fallbackLookAt: Position? = null,
    flattenSingleLayer: Boolean = false,
  ): String {
    val resolvedBalloonUrl = session.balloonAppUrl.ifBlank { DEFAULT_BALLOON_PATH }
    val documentCentroid =
      layers
        .asSequence()
        .flatMap { it.placemarks.asSequence() }
        .firstNotNullOfOrNull { it.geometry?.let(GeometryProtos::representativePosition) }
        ?: fallbackLookAt

    val styleIds = layers.indices.map { index -> "collectone-layer-${index + 1}" }
    val resolvedSubPlotsByLayer = layers.map { layer ->
      layer.placemarks.map { placemark -> KmlSubPlots.resolve(placemark, layer.sampleDesign) }
    }
    val flattenedLayer = if (flattenSingleLayer && layers.size == 1) layers[0] else null

    return buildString {
      appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
      appendLine(
        """<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">"""
      )
      if (flattenedLayer != null) {
        val docId =
          flattenedLayer.layerId.ifBlank { flattenedLayer.datasetId.ifBlank { styleIds[0] } }
        val resolvedName =
          flattenedLayer.name.ifBlank {
            flattenedLayer.datasetId.ifBlank { documentName.ifBlank { "Collect One Survey" } }
          }
        val resolvedDescription = flattenedLayer.description.ifBlank { documentDescription }
        appendLine("""  <Document id="${escapeXml(docId)}">""")
        appendLine("    <name>${escapeXml(resolvedName)}</name>")
        appendLine("    <visibility>${if (flattenedLayer.isVisible) 1 else 0}</visibility>")
        appendLine("    <open>1</open>")
        if (resolvedDescription.isNotBlank()) {
          appendLine("    <description>${escapeXml(resolvedDescription)}</description>")
        }
      } else {
        appendLine("  <Document>")
        appendLine("    <name>${escapeXml(documentName.ifBlank { "Collect One Survey" })}</name>")
        appendLine("    <open>1</open>")
        if (documentDescription.isNotBlank()) {
          appendLine("    <description>${escapeXml(documentDescription)}</description>")
        }
      }
      if (documentCentroid != null) {
        appendLookAt(indent = "    ", center = documentCentroid, range = 1200)
      }
      layers.forEachIndexed { index, layer ->
        appendLayerStyles(
          baseStyleId = styleIds[index],
          layer = layer,
          resolvedSubPlots = resolvedSubPlotsByLayer[index],
          resolvedBalloonUrl = resolvedBalloonUrl,
          surveyId = surveyId,
          surveyTitle = surveyTitle,
          session = session,
        )
      }
      if (flattenedLayer != null) {
        flattenedLayer.placemarks.forEachIndexed { index, placemark ->
          appendPlacemark(
            indent = "    ",
            placemark = placemark,
            styleId = styleIds[0],
            subPlots =
              resolvedSubPlotsByLayer[0].getOrElse(index) { KmlSubPlots.ResolvedSubPlots.EMPTY },
            sampleDesign = flattenedLayer.sampleDesign,
          )
        }
      } else {
        layers.forEachIndexed { index, layer ->
          appendFolder(
            layer = layer,
            styleId = styleIds[index],
            resolvedSubPlots = resolvedSubPlotsByLayer[index],
          )
        }
      }
      appendLine("  </Document>")
      append("</kml>")
    }
  }

  /**
   * Writes a self-refreshing OGC KML 2.2 wrapper `<Document>` containing a `<NetworkLink>` that
   * polls [targetKmlUrl] every [refreshIntervalSeconds] without resetting camera view
   * (`<flyToView>0</flyToView>`) or user folder visibility
   * (`<refreshVisibility>0</refreshVisibility>`).
   */
  fun writeNetworkLinkDocument(
    surveyId: String,
    documentName: String,
    documentDescription: String = "",
    targetKmlUrl: String,
    refreshIntervalSeconds: Int = DEFAULT_REFRESH_INTERVAL_SECONDS,
    networkLinkName: String = "Live Layers",
    lookAt: Position? = null,
  ): String {
    val cleanSurveyId = surveyId.trim().ifEmpty { "survey" }
    val networkLinkId = "collectone-networklink-$cleanSurveyId"
    return buildString {
      appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
      appendLine(
        """<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">"""
      )
      appendLine("  <Document>")
      appendLine("    <name>${escapeXml(documentName.ifBlank { "Collect One Survey" })}</name>")
      appendLine("    <open>1</open>")
      if (documentDescription.isNotBlank()) {
        appendLine("    <description>${escapeXml(documentDescription)}</description>")
      }
      if (lookAt != null) {
        appendLookAt(indent = "    ", center = lookAt, range = 1200)
      }
      appendLine("""    <NetworkLink id="${escapeXml(networkLinkId)}">""")
      appendLine("      <name>${escapeXml(networkLinkName)}</name>")
      appendLine("      <visibility>1</visibility>")
      appendLine("      <open>1</open>")
      appendLine("      <refreshVisibility>0</refreshVisibility>")
      appendLine("      <flyToView>0</flyToView>")
      appendLine("      <Link>")
      appendLine("        <href>${escapeXml(targetKmlUrl)}</href>")
      if (refreshIntervalSeconds > 0) {
        appendLine("        <refreshMode>onInterval</refreshMode>")
        appendLine("        <refreshInterval>$refreshIntervalSeconds</refreshInterval>")
      }
      appendLine("      </Link>")
      appendLine("    </NetworkLink>")
      appendLine("  </Document>")
      append("</kml>")
    }
  }

  private fun StringBuilder.appendFolder(
    layer: LayerFolderSpec,
    styleId: String,
    resolvedSubPlots: List<KmlSubPlots.ResolvedSubPlots>,
  ) {
    val folderId = layer.layerId.ifBlank { layer.datasetId.ifBlank { styleId } }
    appendLine("""    <Folder id="${escapeXml(folderId)}">""")
    appendLine("      <name>${escapeXml(layer.name.ifBlank { layer.datasetId })}</name>")
    appendLine("      <visibility>${if (layer.isVisible) 1 else 0}</visibility>")
    appendLine("      <open>1</open>")
    if (layer.description.isNotBlank()) {
      appendLine("      <description>${escapeXml(layer.description)}</description>")
    }
    val folderCentroid =
      layer.placemarks.firstNotNullOfOrNull {
        it.geometry?.let(GeometryProtos::representativePosition)
      }
    if (folderCentroid != null) {
      appendLookAt(indent = "      ", center = folderCentroid, range = 900)
    }
    layer.placemarks.forEachIndexed { index, placemark ->
      appendPlacemark(
        indent = "      ",
        placemark = placemark,
        styleId = styleId,
        subPlots = resolvedSubPlots.getOrElse(index) { KmlSubPlots.ResolvedSubPlots.EMPTY },
        sampleDesign = layer.sampleDesign,
      )
    }
    appendLine("    </Folder>")
  }

  private fun encodeFormsPayload(forms: List<EmbeddedForm>): String {
    if (forms.isEmpty()) return ""
    val jsonArray =
      forms.joinToString(",", prefix = "[", postfix = "]") { form ->
        val protoB64 =
          Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(FormDef.ADAPTER.encode(form.formDef))
        "{\"id\":${jsonString(form.id)}," +
          "\"title\":${jsonString(form.title)}," +
          "\"version\":${jsonString(form.version)}," +
          "\"protoBase64\":${jsonString(protoB64)}}"
      }
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
      .encode(jsonArray.encodeToByteArray())
  }

  private fun StringBuilder.appendStyle(
    styleId: String,
    colorHex: Long,
    balloonHtml: String,
    iconHref: String = DEFAULT_ICON_HREF,
    iconScale: String = "1.1",
    polyAlpha: Int = 0x4D,
    lineWidth: String = "2.5",
  ) {
    val lineColor = kmlColor(colorHex, alpha = 0xFF)
    val polyColor = kmlColor(colorHex, alpha = polyAlpha)
    val safeCdata = balloonHtml.replace("]]>", "]]]]><![CDATA[>")
    appendLine("""    <Style id="${escapeXml(styleId)}">""")
    appendLine("      <IconStyle>")
    appendLine("        <color>$lineColor</color>")
    appendLine("        <scale>$iconScale</scale>")
    appendLine("        <Icon>")
    appendLine("          <href>${escapeXml(iconHref)}</href>")
    appendLine("        </Icon>")
    appendLine("      </IconStyle>")
    appendLine("      <LineStyle>")
    appendLine("        <color>$lineColor</color>")
    appendLine("        <width>$lineWidth</width>")
    appendLine("      </LineStyle>")
    appendLine("      <PolyStyle>")
    appendLine("        <color>$polyColor</color>")
    appendLine("        <fill>1</fill>")
    appendLine("        <outline>1</outline>")
    appendLine("      </PolyStyle>")
    appendLine("      <BalloonStyle>")
    appendLine("        <bgColor>ffffffff</bgColor>")
    appendLine("        <textColor>ff1f1f1f</textColor>")
    appendLine("        <gx:displayMode>panel</gx:displayMode>")
    appendLine("        <text><![CDATA[$safeCdata]]></text>")
    appendLine("      </BalloonStyle>")
    appendLine("    </Style>")
  }

  private fun StringBuilder.appendLayerStyles(
    baseStyleId: String,
    layer: LayerFolderSpec,
    resolvedSubPlots: List<KmlSubPlots.ResolvedSubPlots>,
    resolvedBalloonUrl: String,
    surveyId: String,
    surveyTitle: String,
    session: SessionConfig,
  ) {
    val hasSubPlots = resolvedSubPlots.any { it.isNotEmpty }
    val balloonHtml =
      if (layer.forms.isNotEmpty()) {
        KmlBalloonHtml.buildTemplateHtml(
          balloonAppUrl = resolvedBalloonUrl,
          surveyId = surveyId,
          surveyTitle = surveyTitle,
          datasetId = layer.datasetId,
          datasetName = layer.name,
          formsPayloadBase64 = encodeFormsPayload(layer.forms),
          session = session,
        )
      } else {
        KmlBalloonHtml.buildReadOnlyPropertiesHtml(
          surveyTitle = surveyTitle,
          datasetName = layer.name.ifBlank { layer.datasetId },
          propertyKeys = collectReadOnlyPropertyKeys(layer.placemarks, resolvedSubPlots),
        )
      }
    val lineWidth = if (hasSubPlots) "2.0" else "2.5"
    appendStyle(
      styleId = baseStyleId,
      colorHex = layer.colorHex,
      balloonHtml = balloonHtml,
      iconHref = DEFAULT_ICON_HREF,
      iconScale = "1.1",
      polyAlpha = if (hasSubPlots) 0x14 else 0x4D,
      lineWidth = lineWidth,
    )
    appendStyle(
      styleId = "$baseStyleId-in-progress",
      colorHex = STATUS_IN_PROGRESS_COLOR_HEX,
      balloonHtml = balloonHtml,
      iconHref = IN_PROGRESS_ICON_HREF,
      iconScale = "1.15",
      polyAlpha = if (hasSubPlots) 0x22 else 0x66,
      lineWidth = lineWidth,
    )
    appendStyle(
      styleId = "$baseStyleId-completed",
      colorHex = STATUS_COMPLETED_COLOR_HEX,
      balloonHtml = balloonHtml,
      iconHref = COMPLETED_ICON_HREF,
      iconScale = "1.2",
      polyAlpha = if (hasSubPlots) 0x2E else 0x80,
      lineWidth = lineWidth,
    )
    val usedStyles =
      layer.placemarks.mapTo(mutableSetOf()) {
        resolvePlacemarkStyleId(baseStyleId, it.status, it.markerSymbol)
      }
    if ("$baseStyleId-needs-review" in usedStyles) {
      appendStyle(
        styleId = "$baseStyleId-needs-review",
        colorHex = STATUS_NEEDS_REVIEW_COLOR_HEX,
        balloonHtml = balloonHtml,
        iconHref = NEEDS_REVIEW_ICON_HREF,
        iconScale = "1.15",
        polyAlpha = if (hasSubPlots) 0x22 else 0x66,
        lineWidth = lineWidth,
      )
    }
    if ("$baseStyleId-flagged" in usedStyles) {
      appendStyle(
        styleId = "$baseStyleId-flagged",
        colorHex = STATUS_FLAGGED_COLOR_HEX,
        balloonHtml = balloonHtml,
        iconHref = FLAGGED_ICON_HREF,
        iconScale = "1.15",
        polyAlpha = if (hasSubPlots) 0x22 else 0x66,
        lineWidth = lineWidth,
      )
    }
  }

  internal fun resolvePlacemarkStyleId(
    baseStyleId: String,
    status: String,
    markerSymbol: String,
  ): String =
    when (resolvePlacemarkStatusSymbol(status = status, markerSymbol = markerSymbol)) {
      "✓" -> "$baseStyleId-completed"
      "◐" -> "$baseStyleId-in-progress"
      "!" -> "$baseStyleId-needs-review"
      "✕" -> "$baseStyleId-flagged"
      else -> baseStyleId
    }

  internal fun resolvePlacemarkStatusSymbol(
    status: String,
    markerSymbol: String,
  ): String {
    val cleanSymbol = markerSymbol.trim()
    if (cleanSymbol.isNotEmpty()) return cleanSymbol
    return when (status.trim().lowercase()) {
      "completed",
      "complete",
      "surveyed" -> "✓"
      "in progress",
      "in_progress" -> "◐"
      "needs review",
      "needs_review" -> "!"
      "flagged" -> "✕"
      "pending" -> "○"
      else -> ""
    }
  }

  internal fun stripLeadingStatusSymbol(title: String): String =
    title.trim().replaceFirst(LEADING_STATUS_PREFIX_REGEX, "").ifBlank { title.trim() }

  internal fun formatPlacemarkName(cleanTitle: String, statusSymbol: String): String =
    if (statusSymbol.isNotEmpty()) "$statusSymbol $cleanTitle" else cleanTitle

  private fun StringBuilder.appendPlacemark(
    indent: String,
    placemark: PlacemarkSpec,
    styleId: String,
    subPlots: KmlSubPlots.ResolvedSubPlots,
    sampleDesign: SampleDesign?,
  ) {
    val centroid =
      placemark.geometry?.let(GeometryProtos::representativePosition)
        ?: subPlots.rings.firstOrNull()?.center?.let {
          GeometryProtos.position(it.lat, it.lng)
        }
    val rawTitle = placemark.title.ifBlank { placemark.geoId.ifBlank { placemark.id } }
    val cleanTitle = stripLeadingStatusSymbol(rawTitle)
    val statusSymbol =
      resolvePlacemarkStatusSymbol(
        status = placemark.status,
        markerSymbol = placemark.markerSymbol,
      )
    val placemarkName = formatPlacemarkName(cleanTitle, statusSymbol)
    val resolvedStyleId = resolvePlacemarkStyleId(styleId, placemark.status, placemark.markerSymbol)
    val lookAtRange = if (subPlots.isNotEmpty) KmlSubPlots.lookAtRange(sampleDesign) else 450

    appendLine("""$indent<Placemark id="${escapeXml(placemark.id)}">""")
    appendLine("$indent  <name>${escapeXml(placemarkName)}</name>")
    appendLine("$indent  <styleUrl>#${escapeXml(resolvedStyleId)}</styleUrl>")
    if (centroid != null) {
      appendLookAt(indent = "$indent  ", center = centroid, range = lookAtRange)
    }
    appendLine("$indent  <ExtendedData>")
    appendExtendedData("$indent    ", "c1_entity_id", placemark.id)
    appendExtendedData("$indent    ", "c1_entity_title", cleanTitle)
    appendExtendedData("$indent    ", "c1_geo_id", placemark.geoId)
    appendExtendedData(
      "$indent    ",
      "c1_latitude",
      centroid?.let { formatDecimal(it.latitude, 6) }.orEmpty(),
    )
    appendExtendedData(
      "$indent    ",
      "c1_longitude",
      centroid?.let { formatDecimal(it.longitude, 6) }.orEmpty(),
    )
    appendExtendedData("$indent    ", "c1_status", placemark.status)
    appendExtendedData("$indent    ", "c1_marker_symbol", placemark.markerSymbol)
    if (subPlots.isNotEmpty) {
      appendExtendedData("$indent    ", "c1_subplot_count", subPlots.rings.size.toString())
    }
    visibleProperties(placemark, subPlots)
      .entries
      .sortedBy { it.key }
      .forEach { (key, value) -> appendExtendedData("$indent    ", key, value) }
    appendLine("$indent  </ExtendedData>")
    KmlGeometryWriter.appendPlacemarkGeometry(
      out = this,
      indent = "$indent  ",
      geometry = placemark.geometry,
      subPlots = subPlots,
    )
    appendLine("$indent</Placemark>")
  }

  private fun visibleProperties(
    placemark: PlacemarkSpec,
    subPlots: KmlSubPlots.ResolvedSubPlots,
  ): Map<String, String> {
    val merged =
      if (subPlots.synthesizedProperties.isEmpty()) {
        placemark.properties
      } else {
        placemark.properties + subPlots.synthesizedProperties
      }
    return merged.filterKeys { it !in PRESENTATION_PROPERTY_KEYS && !it.startsWith("c1_") }
  }

  private fun collectReadOnlyPropertyKeys(
    placemarks: List<PlacemarkSpec>,
    resolvedSubPlots: List<KmlSubPlots.ResolvedSubPlots>,
  ): List<String> =
    placemarks.indices
      .flatMap { index ->
        visibleProperties(
            placemarks[index],
            resolvedSubPlots.getOrElse(index) { KmlSubPlots.ResolvedSubPlots.EMPTY },
          )
          .keys
      }
      .filter { key -> key.isNotBlank() && '$' !in key && '[' !in key && ']' !in key }
      .distinct()
      .sorted()

  private fun StringBuilder.appendExtendedData(indent: String, name: String, value: String) {
    appendLine(
      """$indent<Data name="${escapeXml(name)}"><value>${escapeXml(value)}</value></Data>"""
    )
  }

  private fun StringBuilder.appendLookAt(indent: String, center: Position, range: Int) {
    appendLine("$indent<LookAt>")
    appendLine("$indent  <longitude>${formatDecimal(center.longitude, 6)}</longitude>")
    appendLine("$indent  <latitude>${formatDecimal(center.latitude, 6)}</latitude>")
    appendLine("$indent  <altitude>0</altitude>")
    appendLine("$indent  <range>$range</range>")
    appendLine("$indent  <tilt>0</tilt>")
    appendLine("$indent  <heading>0</heading>")
    appendLine("$indent  <altitudeMode>relativeToGround</altitudeMode>")
    appendLine("$indent</LookAt>")
  }

  /** KML 32-bit hex color in `aabbggrr` order from `0xAARRGGBB` [argb]. */
  fun kmlColor(argb: Long, alpha: Int): String {
    val a = (alpha and 0xFF).toString(16).padStart(2, '0')
    val r = ((argb ushr 16) and 0xFFL).toString(16).padStart(2, '0')
    val g = ((argb ushr 8) and 0xFFL).toString(16).padStart(2, '0')
    val b = (argb and 0xFFL).toString(16).padStart(2, '0')
    return "$a$b$g$r"
  }

  /** Parses `#RRGGBB` or `#AARRGGBB` CSS hex color into an ARGB `Long`. */
  fun parseCssHexColor(raw: String?, fallback: Long = DEFAULT_LAYER_COLOR_HEX): Long {
    val hex = raw?.trim()?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 }
    val parsed = hex?.toLongOrNull(16) ?: return fallback
    return 0xFF000000L or (parsed and 0xFFFFFFL)
  }

  /** [value] with exactly [decimals] decimal places, without locale-dependent formatting. */
  fun formatDecimal(value: Double, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0')
    return if (decimals == 0) "$sign$whole" else "$sign$whole.$fraction"
  }

  /** [text] as a JSON string literal. */
  fun jsonString(text: String): String {
    val builder = StringBuilder("\"")
    text.forEach { c ->
      when {
        c == '"' -> builder.append("\\\"")
        c == '\\' -> builder.append("\\\\")
        c == '\n' -> builder.append("\\n")
        c == '\r' -> builder.append("\\r")
        c == '\t' -> builder.append("\\t")
        c < ' ' -> builder.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> builder.append(c)
      }
    }
    return builder.append('"').toString()
  }

  fun escapeXml(text: String): String =
    buildString(text.length) {
      text.forEach { ch ->
        when (ch) {
          '&' -> append("&amp;")
          '<' -> append("&lt;")
          '>' -> append("&gt;")
          '"' -> append("&quot;")
          '\'' -> append("&apos;")
          else -> append(ch)
        }
      }
    }
}
