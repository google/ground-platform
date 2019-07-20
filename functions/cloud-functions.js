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

'use strict';

const Spreadsheet = require('./spreadsheet');
const KmlWriter = require('./kml-writer');

class CloudFunctions {
  constructor(db, auth) {
    this.db_ = db;
    this.auth_ = auth;
  }

  getSheet_(projectId) {
    return this.db_.fetchSheetsConfig(projectId).then(sheetConfig =>
      sheetConfig &&
      sheetConfig.sheetId &&
      new Spreadsheet(this.auth_, sheetConfig.sheetId));
  }

  exportKml(req, res) {
    const {
      project: providedProjectId,
      featureType: providedFeatureTypeId,
      lang: desiredLanguage,
    } = req.query;

    // TODO: add Schema validation
    // TODO: respect the icon config in the proj setting
    let data = {};
    return this.db_.fetchProject(providedProjectId).then(
      project => {
        if (!project.exists) {
          res.status(404).send('not found');
          return Promise.reject(new Error('project not found: ' + providedProjectId));
        }
        data['projects'] = {};
        data['projects'][project.id] = {}
        data['projects'][project.id]['title'] = project.get('title');
        data['projects'][project.id]['featureTypes'] = {};
        const featureTypes = project.get('featureTypes');
        for (var featureTypeIdVar in featureTypes) {
          const featureType = featureTypes[featureTypeIdVar];
          data['projects'][project.id]['featureTypes'][featureTypeIdVar] = {};
          data['projects'][project.id]['featureTypes'][featureTypeIdVar]['definition'] = featureType;
          data['projects'][project.id]['featureTypes'][featureTypeIdVar]['features'] = {};
        }
        return Promise.all([
          project.ref.collection('features').get(),
          // TODO: Filter records by featureType where specified with something like:
          // project.ref.collection('records').where('featureTypeId', '==', providedFeatureTypeId).get()
          project.ref.collection('records').get()
        ]);
      }
    ).then(
      results => {
        const features = results[0];
        features.forEach(
          feature => {
            const featureTypeId = feature.get('featureTypeId');
            if (providedFeatureTypeId && featureTypeId != providedFeatureTypeId) {
              return;
            }
            const projectId = feature.ref.parent.parent.id;
            let featureTypeMap = data['projects'][projectId]['featureTypes'][featureTypeId];
            featureTypeMap['features'][feature.id] = {};
            featureTypeMap['features'][feature.id]['data'] = feature.data();
            featureTypeMap['features'][feature.id]['records'] = {};
          }
        );
        const records = results[1];
        records.forEach(
          record => {
            const featureTypeId = record.get('featureTypeId');
            if (providedFeatureTypeId && featureTypeId != providedFeatureTypeId) {
              return;
            }
            const featureId = record.get('featureId');
            const projectId = record.ref.parent.parent.id;
            let featureMap = data['projects'][projectId]['featureTypes'][featureTypeId]['features'][featureId];
            if (Object.keys(featureMap).length == 0) {
              // If the related feature itself doesn't exist, skip
              return;
            }
            featureMap['records'][record.id] = record.data();
          }
        );
        let kmlWriter = new KmlWriter(providedProjectId, data, desiredLanguage ? desiredLanguage : '');
        return kmlWriter.getTmpKmlFile();
      }
    ).then(
      tmpKmlFilePath => {
        return res.download(tmpKmlFilePath);
      }
    ).catch(
      err => {
        console.log(err);
        //return res.status(500).end();
        return res.send(data);
      }
    )
  }

  exportCsv(req, res) {
    const {
      project: providedProjectId,
      featureType: providedFeatureTypeId,
      lang: desiredLanguage,
    } = req.query;

    return res.status(500).send('Not supported yet.');
  }

  updateSpreadsheetColumns(req, res) {
    const {
      project: projectId,
      featureType: featureTypeId,
      form: formId,
      lang: lang
    } = req.query;
    return this.getSheet_(projectId).then(sheet => {
      if (!sheet) {
        return res.status(400).send('Project spreadsheet config not found');
      }
      return this.db_.fetchForm(projectId, featureTypeId, formId).then(form => {
        if (!form) {
          return res.status(400).send('Form definition not found');
        }
        const elements = form.elements || [];
        if (!elements.length) {
          return res.status(200).send("Form definition is empty");
        }
        return sheet.getColumnMetadata().then(cols => {
          // Use the end index of the last column as our insertion point.
          const insertAt = cols.length ?
            cols[cols.length - 1].location.dimensionRange.endIndex : 0;
          const existingColumnIds = cols.map(col => col.metadataValue);
          return sheet
            .updateColumns(existingColumnIds, form.elements, insertAt, lang)
            .then(cnt => res.send(`${cnt} columns added`));
        });
      });
    });
  }

  onCreateRecord(change, context) {
    const {
      projectId,
      featureId,
      recordId
    } = context.params;
    const record = change.data();
    const {
      featureTypeId,
      formId
    } = record;
    return this.getSheet_(projectId).then(sheet =>
      sheet && sheet.getColumnIds().then(colIds =>
        this.db_.fetchFeature(projectId, featureId).then(feature =>
          sheet.addRow(feature, featureId, recordId, record, colIds))));
  }

  onUpdateRecord(change, context) {
    const {
      projectId,
      featureId,
      recordId
    } = context.params;
    const record = change.after.data();
    const {
      featureTypeId,
      formId
    } = record;
    return this.getSheet_(projectId).then(sheet =>
      sheet && sheet.getColumnIds().then(colIds =>
        this.db_.fetchFeature(projectId, featureId).then(feature =>
          sheet.updateRow(feature, featureId, recordId, record, colIds))));
  }
}

module.exports = CloudFunctions;