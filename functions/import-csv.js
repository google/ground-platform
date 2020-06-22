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

const { db } = require('./common/context');

// TODO(tiyara): First read a comma-separated string and test the function. Then enable file upload.
function importCsv(req, res) {
  //serverLocation = uploadCsv(fileLocation);
  const {
    project: projectId,
    csv: csvString,
    layer: layerId,
  } = req.query;
  return insertCsv(projectId, layerId, csvString).then(() => res.status(200).send(`Hello World`));
}

function uploadCsv(fileLocation) {
  console.log('Importing new csv file from ' + fileLocation);
  var csvFile = new XMLHttpRequest();
  csvFile.open("GET", fileLocation, true);
  csvFile.onreadystatechange = function () {
    if (csvFile.readyState === 4) {
      if (csvFile.status === 200 || csvFile.status == 0) {
        // TODO: do something if file upload is successful.
      }
    }
  }
  csvFile.send(null);
  console.log('CSV file successfully imported');
}

// TODO(tiyara): Read the text file in a streaming fashion, read one (a few) line and insert and then next.
function insertCsv(projectId, layerId, csvString) {
  //var allTextLines = txtFile.split(/\r\n|\n/);
  const columns = csvString.split(','); // "Name, State, Lat, Long"
  const featureCaption = columns[0] + ", " + columns[1];
  const featureLat = columns[2];
  const featureLong = columns[3];
  return db.insertFeature(projectId,
    { layerId, featureCaption, featureLat, featureLong });
}

module.exports = importCsv;