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

"use strict";

const { db } = require("./common/context");

function exportCsv(req, res) {
  const {
    project: projectId,
    layer: layerId,
    lang: desiredLanguage,
  } = req.query;
  // TODO: Simplify/refactor post G4G19.
  var elements = [];
  var features = [];
  return db
    .fetchProject(projectId)
    .then((project) => {
      if (!project.exists) {
        res.status(404).send("not found");
        return Promise.reject(new Error("project not found: " + projectId));
      }
      res.type("text/csv");
      let form = project.get("layers")[layerId]["forms"];
      elements = form[Object.keys(form)[0]]["elements"];
      var arr = [];
      for (var el in elements) {
        arr.push('"' + escapeQuotes(elements[el]["labels"]["_"]) + '"');
      }
      arr.push('"Latitude"');
      arr.push('"Longitude"');
      res.write(arr.join(",") + "\n");
      return db.fetchFeatures(project.id);
    })
    .then((data) => {
      features = data;
      var promises = [];
      features.docs.forEach((feature) => {
        promises.push(db.fetchRecords(projectId, feature.id));
      });
      return Promise.all(promises);
    })
    .then((records) => {
      var records = records[0]; // TODO: remove this line if fetchRecords give correct data
      features.docs.forEach((feature) => {
        records.docs.forEach((record) => {
          if (record.get("featureId") == feature.id) {
            // TODO: remove this condition if fetchRecords give correct data
            var arr = [];
            for (var el in elements) {
              var value = record.data()["responses"][elements[el]["id"]];
              value = value == (undefined || null) ? "" : value;
              arr.push('"' + escapeQuotes(value) + '"');
            }
            var center = feature.data()["center"];
            arr.push('"' + center["_latitude"] + '"');
            arr.push('"' + center["_longitude"] + '"');
            res.write(arr.join(",") + "\n");
          }
        });
      });
      return res.end();
    })
    .catch((err) => {
      console.error("Export Failed: ", err);
      return res.status(500).end();
    });

  function escapeQuotes(str) {
    return str.replace(/\r?\n/g, "\\n").replace(/"/g, '""');
  }
}

module.exports = exportCsv;
