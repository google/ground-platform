/**
 * @license
 * Copyright 2018 Google LLC
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

const functions = require("firebase-functions");
const onCreateUser = require("./on-create-user");
const exportCsv = require("./export-csv");
const exportKml = require("./export-kml");
const importCsv = require("./import-csv");
const importGeoJson = require("./import-geojson");
const updateColumns = require("./update-columns");
const onCreateRecord = require("./on-create-record");
const onUpdateRecord = require("./on-update-record");
const cors = require("cors")({ origin: true });

/**
 * Wrapper for HTTP request handlers. Captures uncaught exceptions to prevent
 * leaking stack traces or other debug details to clients.
 */
function onHttpsRequest(handler) {
  return functions.https.onRequest((req, res) =>
    cors(req, res, () => handler(req, res).catch((err) => onError(res, err)))
  );
}

function onError(res, err) {
  console.error(err);
  res.status(500).send("Internal error");
}

// Create user profile in database when user first logs in.
exports.onCreateUser = functions.auth.user().onCreate(onCreateUser);

exports.importCsv = onHttpsRequest(importCsv);

exports.importGeoJson = onHttpsRequest(importGeoJson);

exports.exportCsv = onHttpsRequest(exportCsv);
