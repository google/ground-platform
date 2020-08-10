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
var fs = require('fs');
var parse = require('csv-parse');

// TODO(tiyara): First read a comma-separated string and test the function. Then enable file upload.

function importCsv(req, res) {
  var promises = [];
  // Catch the URL params.
  const {
    projectId,
    layerId,
  } = req.query
  console.log(req.headers);
  console.log("\nProject Id is " + req.body[projectId]);
  // Stream the csv file.
  var csvData = [];
  req.on('data', (data) => {
    console.log(data.toString());
  });
  req.on('end', () => {
    res.send('ok');
  });
  return Promise.resolve();
}



// function importCsv(req, res) {
//   var promises = [];
//   // Catch the URL params.
//   const {
//     projectId = projectId,
//     layerId = layerId,
//     csvFile = csvFile,
//   } = req.query

//   // Stream the csv file.
//   var csvData = [];
//   fs.createReadStream(csvFile)
//     .pipe(parse({ delimiter: ',', from_line: 2 }))
//     .on('data', function (csvrow) {
//       console.log(csvrow);
//       const featureCaption = csvrow[0] + ":" + csvrow[1];
//       const featureLat = csvrow[2];
//       const featureLong = csvrow[3];
//       promises.push(db.insertFeature(projectId, { layerId, featureCaption, featureLat, featureLong }));
//       csvData.push(csvrow);
//     })
//     .on('end', () => {
//       console.log(csvData);
//     });
//   return Promise.all(promises);
// }

module.exports = importCsv;