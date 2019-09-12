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
  let numberOfRecords = 0;
  return db.fetchProject(projectId).then(
    project => {
      if (!project.exists) {
        res.status(404).send('not found');
        return Promise.reject(new Error('project not found: ' + projectId))
      }
      return Promise.all([
        db.fetchForm(project.id, featureTypeId),
        db.fetchRecords(project.id, featureTypeId)
      ]);
    }
  ).then(
    results => {
      return readFormAndRecords(results, data);
    }
  ).then(
    data => {
      return streamToRes();
    }
  ).catch(
    err => {
      console.log(err);
      //return res.status(500).end();
      return res.send(data);
    }
  )

  function readFormAndRecords(results) {
    const form = results[0];
    for(var prop in form) {
      if(form.hasOwnProperty(prop)) {
        form[prop]['elements'].forEach(
          field => {
            data[field['id']] = [field['labels']['_']];
          }
        );
      }
    }
  
    const records = results[1];
    numberOfRecords = records.length;
    records.forEach(
      record => {
        const responses = record.get('responses');
        for(var prop in responses) {
          toPush = response[prop];
          if (Array.isArray(toPush)) {
            toPush = '"' + escapeQuotes(toPush.join()) + '"';
          } else {
            toPush = '"' + escapeQuotes(toPush) + '"';
          }
          data[prop].push(toPush);
        }
      }
    );
    return data;
  }
  
  function streamToRes() {
    res.type('csv');
      for (var i = 0; i <= numberOfRecords; i++) {
        toSend = '';
        for (var prop in data) {
          toSend += data[prop][i] + ',';
        }
        toSend.slice(0, -1);
        res.write(toSend + '\n');
      }
      return res.end();
  }

  function escapeQuotes( str ) {
    return (str + '').replace(/[\\"']/g, '\\$&').replace(/\u0000/g, '\\0');
  }
}



module.exports = exportCsv;