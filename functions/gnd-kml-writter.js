const fs = require('fs');
const path = require('path');
const os = require('os');

class GndKmlWritter {

  constructor(projName, rawData, desiredLanguage) {
    this.projName_ = projName;
    this.rawData_ = rawData;
    this.desiredLanguage_ = desiredLanguage;
  }

  getTmpKmlFile() {
    const kmlString = GndKmlWritter.buildKml_(this.rawData_, this.desiredLanguage_);

    return new Promise((resolve, reject) => {
      fs.mkdtemp(path.join(os.tmpdir(), 'gndExport-'), (err, folderPath) => {
        if (err) reject(err);
        return resolve(folderPath)
      });
    }).then(
      folderPath => {
        const filePath = folderPath + '/' + this.projName_ + '.kml'
        return new Promise((resolve, reject) => {
          fs.writeFile(filePath, kmlString, (err) => {
            if (err) reject(err);
            console.log('KML data has been written to: ' + filePath);
            return resolve(filePath);
          });
        });
      }
    );
  }

  static buildKml_(rawData, desiredLanguage) {
    let kml = '';
    kml += '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<kml xmlns="http://www.opengis.net/kml/2.2">\n';

    for (var projectId in rawData['projects']) {
      const project = rawData['projects'][projectId];
      kml = GndKmlWritter.addProjectToKml_(kml, project, desiredLanguage);
    }

    kml += '</kml>\n';
    return kml;
  }

  static addProjectToKml_(kml, project, desiredLanguage) {
    kml += '<Document>\n';
    kml +=
      '<name>' +
      GndKmlWritter.getFieldWithDesiredLanguage_(
        project['title'], [desiredLanguage]) +
      '</name>\n';

    for (var featureTypeId in project['featureTypes']) {
      const features = project['featureTypes'][featureTypeId]['features'];
      const featureTypeDef = project['featureTypes'][featureTypeId]['definition'];
      kml = GndKmlWritter.addFeatureTypeToKml_(kml, featureTypeId, featureTypeDef, features, desiredLanguage);
    }

    kml += '</Document>\n';
    return kml;
  }

  // TODO: Add different styles for different featureTypeId
  static addFeatureTypeToKml_(kml, featureTypeId, featureTypeDef, features, desiredLanguage) {
    kml += '<Folder>\n';
    kml += '<name>' + featureTypeId + '</name>\n';
    for (var featureId in features) {
      const feature = features[featureId];
      kml = GndKmlWritter.addFeatureToKml_(kml, featureTypeDef, featureId, feature, desiredLanguage);
    }

    kml += '</Folder>\n';
    return kml;
  }

  static addFeatureToKml_(kml, featureTypeDef, featureId, feature, desiredLanguage) {
    kml += '<Placemark>\n';
    kml += '<name>' + featureId + '</name>\n';

    const records = feature['records'];
    if (Object.keys(records).length > 0) {
      kml = GndKmlWritter.addRecordsToKml_(kml, featureTypeDef, records, desiredLanguage);
    }
    const featureData = feature['data'];
    kml = GndKmlWritter.addFeatureDataToKml_(kml, featureData);

    kml += '</Placemark>\n';
    return kml;
  }

  static addRecordsToKml_(kml, featureTypeDef, records, desiredLanguage) {
    kml += '<description>';
    kml += '<style>table, th, td {\n' +
      'border: 1px solid black;\n' +
      'border-collapse: collapse;\n' +
      'table-layout: fixed;\n' +
      'text-align: left;\n' +
      'width: 100% ;\n' +
      '}</style>\n';
    let recordCnt = 0;
    for (var recordId in records) {
      recordCnt += 1;
      kml += '<table>';
      kml += '<caption align="left">Record: ' + recordCnt + '</caption>';
      const record = records[recordId];
      const formDef = featureTypeDef['forms'][record['formId']];
      kml += GndKmlWritter.recordToHtmlTableRow(formDef, record, desiredLanguage);
      kml += '</table>';
    }
    kml += '</description>\n';
    return kml;
  }

  static recordToHtmlTableRow(formsDef, record, desiredLanguage) {
    let row = '';
    // Add modifiedBy
    row += '<tr>';
    row += '<th>Modified Time</th>';
    row += '<td>' + record['clientTimeModified'] + '</td>';
    row += '</tr>';
    // Add Responses
    const questions = formsDef['elements'];
    const responses = record['responses'];
    for (var question in questions) {
      const questionId = questions[question]['id'];
      const questionLabels = questions[question]['labels'];

      const responseCode = responses[questionId] ? responses[questionId][0] : null;
      let response = responseCode ? responseCode : ' - ';
      if (questions[question]['options'] && responseCode) {
        let optionLabelsMapping = new Map();
        questions[question]['options'].forEach(
          option => {
            if (option['code'] == responseCode) {
              response = GndKmlWritter.getFieldWithDesiredLanguage_(
                option['labels'], desiredLanguage);
            }
          }
        );
      }
      
      row += '<tr>'
      row +=
        '<th>' +
        GndKmlWritter.getFieldWithDesiredLanguage_(
          questionLabels, desiredLanguage)
        + '</th>';
      row += '<td>' + response + '</td>';
      row += '</tr>';
    }
    return row;
  }

  static addFeatureDataToKml_(kml, featureData) {
    const geoPoint = featureData['center'];
    kml += '<Point>\n';
    kml += '<coordinates>' + geoPoint.longitude + ',' + geoPoint.latitude + ',0</coordinates>\n';
    kml += '</Point>\n';
    return kml;
  }

  static getFieldWithDesiredLanguage_(data, desiredLanguage) {
    if (!data) {
      return '';
    }
    const keys = new Set(Object.keys(data));
    if (keys.size == 0) {
      return '';
    } else if (keys.has(desiredLanguage)) {
      return data[desiredLanguage];
    } else if (keys.has('_')) {
      return data['_'];
    } else {
      return data[Object.keys(data)[0]];
    }
  }
}

module.exports = GndKmlWritter;