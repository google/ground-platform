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
const {getSheet} = require('./common/sheet-config');

function updateColumns(req, res) {
  const {
    survey: surveyId,
    featureType: featureTypeId,
    task: taskId,
    lang: lang
  } = req.query;
  return getSheet(surveyId).then(sheet => {
    if (!sheet) {
      return res.status(400).send('Survey spreadsheet config not found');
    }
    return db.fetchTask(surveyId, featureTypeId, taskId).then(task => {
      if (!task) {
        return res.status(400).send('Task definition not found');
      }
      const elements = task.elements || [];
      if (!elements.length) {
        return res.status(200).send("Task definition is empty");
      }
      return sheet.getColumnMetadata().then(cols => {
        // Use the end index of the last column as our insertion point.
        const insertAt = cols.length ?
          cols[cols.length - 1].location.dimensionRange.endIndex : 0;
        const existingColumnIds = cols.map(col => col.metadataValue);
        return sheet
          .updateColumns(existingColumnIds, task.elements, insertAt, lang)
          .then(cnt => res.send(`${cnt} columns added`));
      });
    });
  });
}

module.exports = updateColumns;