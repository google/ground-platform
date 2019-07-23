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
const KmlWriter = require('./common/kml-writer');

function exportKml(req, res) {
  const {
    project: providedProjectId,
    featureType: providedFeatureTypeId,
    lang: desiredLanguage,
  } = req.query;

  // TODO: add Schema validation
  // TODO: respect the icon config in the proj setting
  let data = {};
  return db.fetchProject(providedProjectId).then(
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

module.exports = {exportKml};