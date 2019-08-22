const fs = require('fs');
const path = require('path');
const os = require('os');

class CsvWriter {
  constructor (projName, rawData, desiredLanguage) {
    this.projName_ = projName;
    this.rawData_ = rawData;
    this.desiredLanguage_ = desiredLanguage;
  }

  getTmpCsvFile() {
    const csvString = CsvWriter.buildCsv_(this.rawData_, this.desiredLanguage_);

    return new Promise((resolve, reject) => {
      fs.mkdtemp(path.join(os.tmpdir(), 'export-'), (err, folderPath) => {
        if (err) reject(err);
        return resolve(folderPath)
      });
    }).then(
      folderPath => {
        const filePath = folderPath + '/' + this.projName_ + '.csv'
        return new Promise((resolve, reject) => {
          fs.writeFile(filePath, kmlString, (err) => {
            if (err) reject(err);
            console.log('CSV data has been written to: ' + filePath);
            return resolve(filePath);
          });
        });
      }
    );
  }

  static buildCsv_(rawData, desiredLanguage) {
    let csv = '';
    let index = 0;
    while (true) {
    for (var prop in rawData) {
        csv += rawData[prop][index] + ',';
    }
    index += 1;
    csv.slice(0, -1);
    csv += '\n';
    }
  }
}

module.exports = CsvWriter;