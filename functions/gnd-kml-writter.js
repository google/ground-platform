const fs = require('fs');
const path = require('path');
const os = require('os');

class GndKmlWritter {

  constructor(rawData, desiredLanguage) {
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
        const filePath = folderPath + '/out.kml'
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
        project['data']['title'], [desiredLanguage]) +
      '</name>\n';
    kml +=
      '<description>' +
      GndKmlWritter.getFieldWithDesiredLanguage_(
        project['data']['description'], [desiredLanguage]) +
      '</description>\n';

    for (var featureTypeId in project['featureTypes']) {
      const features = project['featureTypes'][featureTypeId]['features'];
      kml = GndKmlWritter.addFeatureTypeToKml_(kml, featureTypeId, features, desiredLanguage);
    }

    kml += '</Document>\n';
    return kml;
  }

  // TODO: Add different styles for different featureTypeId
  static addFeatureTypeToKml_(kml, featureTypeId, features, desiredLanguage) {
    kml += '<Folder>\n';
    kml += '<name>' + featureTypeId + '</name>\n';
    for (var featureId in features) {
      const feature = features[featureId];
      kml = GndKmlWritter.addFeatureToKml_(kml, featureId, feature, desiredLanguage);
    }

    kml += '</Folder>\n';
    return kml;
  }

  static addFeatureToKml_(kml, featureId, feature, desiredLanguage) {
    kml += '<Placemark>\n';
    kml += '<name>' + featureId + '</name>\n';

    const records = feature['records'];
    if (Object.keys(records).length > 0) {
      kml = GndKmlWritter.addRecordsToKml_(kml, records, desiredLanguage);
    }
    const featureData = feature['data'];
    kml = GndKmlWritter.addFeatureDataToKml_(kml, featureData);

    kml += '</Placemark>\n';
    return kml;
  }

  static addRecordsToKml_(kml, records, desiredLanguage) {
    kml += '<description>';
    kml += '<style>table, th, td {\n' +
      'border: 1px solid black;\n' +
      'border-collapse: collapse;\n' +
      '}</style>\n';
    kml += '<table>';
    kml += '<tr><th>RecordId</th><th>Record</th></tr>';
    for (var recordId in records) {
      const record = records[recordId];
      kml += '<tr>';
      kml += '<td>' + recordId + '</td>';
      kml += '<td><pre>' + JSON.stringify(record, null, 2) + '</pre></td>';
      kml += '</tr>';
    }
    kml += '</table>';
    kml += '</description>\n';
    return kml;
  }

  static addFeatureDataToKml_(kml, featureData) {
    const geoPoint = featureData['center'];
    kml += '<Point>\n';
    kml += '<coordinates>' + geoPoint.longitude + ',' + geoPoint.latitude + ',0</coordinates>\n';
    kml += '</Point>\n';
    return kml;
  }

  static getFieldWithDesiredLanguage_(data, desiredLanguage) {
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