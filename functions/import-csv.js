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

const HttpStatus = require("http-status-codes");
const { firestore } = require("firebase-admin");
const csvParser = require("csv-parser");
const Busboy = require("busboy");
const { db } = require("./common/context");

const closeConnection = (code, reason) => {
  req.unpipe(busboy);
  res.writeHead(code || 400, { Connection: "close" });
  res.end(reason || "Bad Request");
};

/**
 * Streams a multipart HTTP POSTed form containing a CSV 'file' and required
 * 'project' id and 'layer' id to the database.
 */
function importCsv(req, res) {
  // Based on https://cloud.google.com/functions/docs/writing/http#multipart_data
  if (req.method !== "POST") {
    return res.status(HttpStatus.METHOD_NOT_ALLOWED).end();
  }
  const busboy = new Busboy({ headers: req.headers });

  // Dictionary used to accumulate form field values, keyed by field name.
  const params = {};

  // Handle non-file fields in the form. project and layer must appear
  // before the file for the file handler to work properly.
  busboy.on("field", (key, val) => {
    params[key] = val;
  });

  // This code will process each file uploaded.
  busboy.on("file", (key, file, _) => {
    const { project: projectId, layer: layerId } = params;
    if (!projectId || !layerId) {
      return res.status(HttpStatus.BAD_REQUEST).end();
    }
    console.log(
      `Importing features into project '${projectId}', layer '${layerId}'..`
    );

    // Pipe file through CSV parser lib, inserting each row in the db as it is
    // received.

    file.pipe(csvParser()).on("data", async (row) => {
      try {
        await insertRow(projectId, layerId, row);
      } catch (err) {
        console.error(err);
        await res.status(HttpStatus.BAD_REQUEST).end("{}");
        // TODO(#525): Abort stream on error. How?
      }
    });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on("finish", async () => {
    // TODO(#525): Return more meaningful errors.
    await res.status(HttpStatus.OK).end("{}");
  });

  busboy.on("error", (err) => {
    console.err("Busboy error", err);
    next(err);
  });
  busboy.end(req.rawBody);
}

/**
 * Transforms a dictionary of string in the form A->B[] into a dictionary of
 * strings in the form B->A. Values in B[] are assumed to appear at most once
 * in the array values in the the provided dictionary.
 */
function invertAndFlatten(obj) {
  return Object.keys(obj)
    .flatMap((k) => obj[k].map((v) => ({ k, v })))
    .reduce((o, { v, k }) => {
      o[v] = k;
      return o;
    }, {});
}

/**
 * Dictionary of lowercase aliases for recognized feature attributes. Case
 * is ignored when mapping column aliases to feature attributes.
 */
const SPECIAL_COLUMN_NAMES = invertAndFlatten({
  id: ["id", "key"],
  caption: ["caption", "name", "label"],
  lat: ["lat", "latitude", "y"],
  lng: ["lng", "lon", "long", "lng", "x"],
});

async function insertRow(projectId, layerId, row) {
  const feature = csvRowToFeature(row, layerId);
  if (feature) {
    await db.insertFeature(projectId, feature);
  }
}

/**
 * Convert the provided row (key-value pairs) and layerId into a Feature for
 * insertion into the data store.
 */
function csvRowToFeature(row, layerId) {
  let data = { layerId };
  let attributes = {};
  for (const columnName in row) {
    const featureKey = SPECIAL_COLUMN_NAMES[columnName.toLowerCase()];
    const value = row[columnName];
    if (featureKey) {
      data[featureKey] = value;
    } else {
      attributes[columnName] = value;
    }
  }
  let { lat, lng, ...feature } = data;
  lat = Number.parseFloat(lat);
  lng = Number.parseFloat(lng);
  if (isNaN(lat) || isNaN(lng)) {
    return null;
  }
  feature["location"] = new firestore.GeoPoint(lat, lng);
  if (Object.keys(attributes).length > 0) {
    feature["attributes"] = attributes;
  }
  return feature;
}

module.exports = importCsv;
