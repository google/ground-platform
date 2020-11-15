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

function importCsv(req, res) {
  // Based on https://cloud.google.com/functions/docs/writing/http#multipart_data
  if (req.method !== "POST") {
    return res.status(HttpStatus.METHOD_NOT_ALLOWED).end();
  }
  const busboy = new Busboy({ headers: req.headers });

  // Dictionary used to accumulate form field values, keyed by field name.
  const params = {};

  // Handle non-file fields in the form. projectId and layerId must appear
  // before the file for the file handler to work properly.
  busboy.on("field", (key, val) => {
    params[key] = val;
  });

  // This code will process each file uploaded.
  busboy.on("file", (key, file, _) => {
    const { projectId, layerId } = params;
    if (!projectId || !layerId) {
      return res.status(HttpStatus.BAD_REQUEST).end();
    }
    console.log(
      `Importing features into project '${projectId}', layer '${layerId}'..`
    );

    // Pipe file through CSV parser lib, inserting each row in the db as it is
    // received.
    file.pipe(csvParser()).on("data", async (row) => {
      console.log("Processing row: ", JSON.stringify(row));
      await insertRow(projectId, layerId, row);
    });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on("finish", async () => {
    res.sendStatus(HttpStatus.OK);
  });
  busboy.end(req.rawBody);
}

const SPECIAL_COLUMN_NAMES = {
  id: "id",
  name: "caption",
  label: "caption",
  caption: "caption",
  lat: "lat",
  latitude: "lat",
  y: "lat",
  lng: "lng",
  long: "long",
  longitude: "lng",
  x: "lng",
};

async function insertRow(projectId, layerId, record) {
  const feature = csvRowToFeature(record);
  if (feature) {
    await db.insertFeature(projectId, layerId, feature);
  }
}

function csvRowToFeature(row) {
  let attributes = {};
  for (key in row) {
    const featureKey = SPECIAL_COLUMN_NAMES[key.toLowerCase()];
    const value = row[key];
    if (featureKey) {
      feature[featureKey] = value;
    } else {
      attributes[key] = value;
    }
  }
  let { lat, lng, ...feature } = feature;
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
