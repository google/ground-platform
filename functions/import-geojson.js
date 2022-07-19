/**
 * @license
 * Copyright 2021 Google LLC
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
const { db } = require("./common/context");
const Busboy = require("busboy");
const JSONStream = require("JSONStream");

/**
 * Read the body of a multipart HTTP POSTed form containing a GeoJson 'file'
 * and required 'project' id and 'job' id to the database.
 */
async function importGeoJson(req, res) {
  if (req.method !== "POST") {
    return res.status(HttpStatus.METHOD_NOT_ALLOWED).end();
  }

  const busboy = new Busboy({ headers: req.headers });

  // Dictionary used to accumulate form field values, keyed by field name.
  const params = {};

  // Accumulates Promises for insert operations so we don't finalize the res
  // stream before operations are complete.
  let inserts = [];

  // Handle non-file fields in the form. project and job must appear
  // before the file for the file handler to work properly.
  busboy.on("field", (key, val) => {
    params[key] = val;
  });

  // This code will process each file uploaded.
  busboy.on("file", (_field, file, _filename) => {
    const { project: projectId, job: jobId } = params;
    if (!projectId || !jobId) {
      return res
        .status(HttpStatus.BAD_REQUEST)
        .end(JSON.stringify({ error: "Invalid request" }));
    }
    console.log(
      `Importing GeoJSON into project '${projectId}', job '${jobId}'`
    );
    // Pipe file through JSON parser lib, inserting each row in the db as it is
    // received.
    let geoJsonType = null;
    file.pipe(JSONStream.parse("type")).on("data", (data) => {
      geoJsonType = data;
    });

    file
      .pipe(JSONStream.parse(["features", true]))
      .on("data", async (geoJsonFeature) => {
        if (geoJsonType !== "FeatureCollection") {
          // TODO: report error to user
          console.debug(`Invalid ${geoJsonType}`);
          return res.status(HttpStatus.BAD_REQUEST).end();
        }
        if (geoJsonFeature.type != "Feature") {
          console.debug(`Skipping feature with type ${geoJsonFeature.type}`);
          return;
        }
        try {
          const feature = geoJsonToGroundFeature(geoJsonFeature, jobId);
          if (feature) {
            inserts.push(db.insertFeature(projectId, feature));
          }
        } catch (err) {
          console.error(err);
          res.unpipe(busboy);
          await res
            .status(HttpStatus.BAD_REQUEST)
            .end(JSON.stringify({ error: err.message }));
          // TODO(#525): Abort stream on error. How?
        }
      });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on("finish", async () => {
    await Promise.all(inserts);
    const count = inserts.length;
    console.log(`${count} features imported`);
    await res.status(HttpStatus.OK).end(JSON.stringify({ count }));
  });

  busboy.on("error", (err) => {
    console.err("Busboy error", err);
    next(err);
  });

  busboy.end(req.rawBody);
}

/**
 * Convert the provided GeoJSON Feature and jobId into a Feature for
 * insertion into the Ground data store.
 */
function geoJsonToGroundFeature(geoJsonFeature, jobId) {
  // TODO: Migrate Android app to use geometry for points instead of 'location'.
  return {
    jobId,
    properties: geoJsonFeature.properties,
    // TODO: Remove once web app moves over to using 'geometry' field.
    geoJson: JSON.stringify(geoJsonFeature),
    // TODO: Convert to object (incl nested arrays) and store in 'geometry'.
    // TODO: Add created/modified metadata.
  };
}

module.exports = importGeoJson;
