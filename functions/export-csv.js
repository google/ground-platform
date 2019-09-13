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

const {db} = require('./common/context');

function exportCsv(req, res) {
  const {
    project: projectId,
    featureType: featureTypeId,
    lang: desiredLanguage,
  } = req.query;

  let data = {};
  return db.fetchProject(projectId).then(
    project => {
      if (!project.exists) {
        res.status(404).send('not found');
        return Promise.reject(new Error('project not found: ' + projectId))
      }
      res.type('text/csv');
      let form = project.get('featureTypes')[featureTypeId]['forms'];
      let elements = form[Object.keys(form)[0]]['elements'];
      var arr = []
      for (var el in elements) {
        arr.push('"' + escapeQuotes(elements[el]['labels']['_']) + '"');
      }
      res.write(arr.join() + '\n');
      
      return Promise.all(
        [db.fetchRecords(project.id, featureTypeId),
          elements]);
    }
  ).then(
    data => {
      var records = data[0];
      var elements = data[1];
      records.docs.forEach(doc => {
        var arr = [];
        if (doc.data()['featureTypeId'] == featureTypeId) {
          for (var el in elements) {
            var value = escapeQuotes(doc.data()['responses'][elements[el]['id']]);
            value = value === undefined ? '' : value;
            arr.push('"' + value + '"');
          }
          res.write(arr.join() + '\n')
        }
      })

      return res.end();
    }
  ).catch(
    err => {
      console.error("=Export Failed= " , err);
      return res.status(500).end();
    }
  )

  function escapeQuotes(str) {
    return (str + '').replace(/[\\"']/g, '\\$&').replace(/\u0000/g, '\\0');
  }
}



module.exports = exportCsv;