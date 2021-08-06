/**
 * @license
 * Copyright 2020 Google LLC
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

"use strict";

const csv = require("@fast-csv/format");
const { db } = require("./common/context");
const wkt = require("wkt");

// TODO: Refactor into meaningful pieces.
async function exportCsv(req, res) {
  const { project: projectId, layer: layerId } = req.query;
  const project = await db.fetchProject(projectId);
  if (!project.exists) {
    res.status(404).send("Project not found");
    return;
  }
  console.log(`Exporting project '${projectId}', layer '${layerId}'`);

  const layers = project.get("layers") || {};
  const layer = layers[layerId] || {};
  const layerName = layer.name && layer.name["en"];
  const forms = layer["forms"] || {};
  const form = Object.values(forms)[0] || {};
  const elementMap = form["elements"] || {};
  const elements = Object.keys(elementMap)
    .map((elementId) => ({ id: elementId, ...elementMap[elementId] }))
    .sort((a, b) => a.index - b.index);

  const headers = [];
  headers.push("feature_id");
  headers.push("feature_name");
  headers.push("latitude");
  headers.push("longitude");
  headers.push("geometry");
  elements.forEach((element) => {
    const labelMap = element["label"] || {};
    const label = Object.values(labelMap)[0] || "Unnamed field";
    headers.push(label);
  });

  res.type("text/csv");
  res.setHeader(
    "Content-Disposition",
    "attachment; filename=" + getFileName(layerName)
  );
  const csvStream = csv.format({
    delimiter: ",",
    headers,
    includeEndRowDelimiter: true,
    rowDelimiter: "\n",
    quoteColumns: true,
    quote: '"',
  });
  csvStream.pipe(res);

  const features = await db.fetchFeaturesByLayerId(project.id, layerId);
  const observations = await db.fetchObservationsByLayerId(project.id, layerId);

  // Index observations by feature id in memory. This consumes more
  // memory than iterating over and streaming both feature and observation`
  // collections simultaneously, but it's easier to read and maintain. This will
  // likely need to be optimized to scale to larger datasets.
  const observationsByFeature = {};
  observations.forEach((observation) => {
    const featureId = observation.get("featureId");
    const arr = observationsByFeature[featureId] || [];
    arr.push(observation.data());
    observationsByFeature[featureId] = arr;
  });

  features.forEach((feature) => {
    const featureId = feature.id;
    const location = feature.get("location") || {};
    const observations = observationsByFeature[featureId] || [{}];
    observations.forEach((observation) => {
      const row = [];
      row.push(getId(feature));
      row.push(getLabel(feature));
      row.push(location["_latitude"] || "");
      row.push(location["_longitude"] || "");
      row.push(toWkt(feature.get("geoJson")) || "");
      const responses = observation["responses"] || {};
      elements
        .map((element) => getValue(element, responses))
        .forEach((value) => row.push(value));
      csvStream.write(row);
    });
  });
  csvStream.end();
}

function toWkt(geoJsonString) {
  const geoJsonObject = parseGeoJson(geoJsonString);
  const geometry = getGeometry(geoJsonObject);
  return geometry ? wkt.stringify(geometry) : "";
}

function parseGeoJson(jsonString) {
  if (!jsonString) {
    return null;
  }
  try {
    return JSON.parse(jsonString);
  } catch (e) {
    return null;
  }
}

function getGeometry(geoJsonObject) {
  if (!geoJsonObject || typeof geoJsonObject !== "object") {
    return null;
  }
  return geoJsonObject.geometry;
}

function getId(feature) {
  const properties = feature.get("properties") || {};
  return (
    feature.get("id") ||
    properties["ID"] ||
    properties["id"] ||
    properties["id_prod"] ||
    ""
  );
}

function getLabel(feature) {
  const properties = feature.get("properties") || {};
  return (
    properties["caption"] || properties["label"] || properties["title"] || ""
  );
}
/**
 * Returns the string representation of a specific form element response.
 */
function getValue(element, responses) {
  const response = responses[element.id] || "";
  if (
    element.type === "multiple_choice" &&
    Array.isArray(response) &&
    element.options
  ) {
    return response
      .map((id) => getMultipleChoiceValues(id, element))
      .join(", ");
  } else {
    return response;
  }
}

/**
 * Returns the code associated with a specified multiple choice option, or if
 * the code is not defined, returns the label in English.
 */
function getMultipleChoiceValues(id, element) {
  const option = element.options[id];
  // TODO: i18n.
  return option.code || option.label["en"] || "";
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(layerName) {
  layerName = layerName || "ground-export";
  const fileBase = layerName.toLowerCase().replace(/[^a-z0-9]/gi, "-");
  return `${fileBase}.csv`;
}

module.exports = exportCsv;
