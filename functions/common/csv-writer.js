const fs = require('fs');
const path = require('path');
const os = require('os');

class CsvWriter {
  constructor (projName, rawData, desiredLanguage) {
    this.projName_ = projName;
    this.rawData_ = rawData;
    this.desiredLanguage_ = desiredLanguage;
    this.numberOfRecords_ = numberOfRecords;
  }

  getTmpCsvFile() {
    const csvString = CsvWriter.buildCsv_(this.rawData_, this.desiredLanguage_, this.numberOfRecords_);

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

  static buildCsv_(rawData, desiredLanguage, numberOfRecords) {
    let csv = '';
    for (i = 0; i <= numberOfRecords; i++) {
      for (var prop in rawData) {
          csv += rawData[prop][i] + ',';
      }
      csv.slice(0, -1);
      csv += '\n';
    }
  }
}

module.exports = CsvWriter;