'use strict';

const { db } = require('./common/context');

// TODO(tiyara): First read a comma-separated string and test the function. Then enable file upload.
function importCsv(req, res) {
  //serverLocation = uploadCsv(fileLocation);
  const {
    project: projectId,
    csv: csvString,
    layer: layerId,
  } = req.query;
  return insertCsv(projectId, layerId, csvString).then(() => res.status(200).send(`Hello World`));
}

function uploadCsv(fileLocation) {
  console.log('Importing new csv file from ' + fileLocation);
  var csvFile = new XMLHttpRequest();
  csvFile.open("GET", fileLocation, true);
  csvFile.onreadystatechange = function () {
    if (csvFile.readyState === 4) {
      if (csvFile.status === 200 || csvFile.status == 0) {
        // TODO: do something if file upload is successful.
      }
    }
  }
  csvFile.send(null);
  console.log('CSV file successfully imported');
}

// TODO(tiyara): Read the text file in a streaming fashion, read one (a few) line and insert and then next.
function hardcodedInsert(req, res) {
  return db.hardcodedInsert().then(() => res.status(200).send(`Hardcoded insert done!`));
}

module.exports = hardcodedInsert;