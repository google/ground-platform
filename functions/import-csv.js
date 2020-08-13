/**
 * @license
 * Copyright 2019 Google LLC
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

'use strict';

"use strict";

const HttpStatus = require("http-status-codes");
const { firestore } = require('firebase-admin');
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
  busboy.on("file", (key, file, filename) => {
    const { projectId, layerId } = params;
    if (key != "csvfile" || !projectId || !layerId) {
      return res.status(HttpStatus.BAD_REQUEST).end();
    }
    console.log(
      `Importing features into project '${projectId}', layer '${layerId}'..`
    );

    // Pipe file through CSV parser lib, inserting each row in the db as it is
    // received.
    file.pipe(csvParser()).on("data", (record) => {
      console.log("Processing row: ", JSON.stringify(record));
      db.insertFeature(projectId, layerId, csvRowToFeature(record));
    });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on("finish", async () => {
    res.sendStatus(HttpStatus.OK);
  });

  busboy.end(req.rawBody);
}

function csvRowToFeature(record) {
  var feature = {
    caption: record.name + "," + record.state,
    location: new firestore.GeoPoint(Number.parseFloat(record.lat)
    , Number.parseFloat(record.long))
  };
 return feature;
}

module.exports = importCsv;