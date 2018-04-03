'use strict';

const google = require('googleapis');

const sheets = google.sheets('v4');

// TODO: Use single quotes everywhere except JSON for consistency.
const ELEMENT_ID_COLUMN_METADATA_KEY = 'elementId';
const RECORD_ID_ROW_METADATA_KEY = 'recordId';
const LAT_COLUMN_ID = '_latColumn';
const LNG_COLUMN_ID = '_lngColumn';
const GEOPOINT_LAT = '_latitude';
const GEOPOINT_LNG = '_longitude';

function columnElementIdsRequest() {
  return {
    dataFilters: [
      {
        developerMetadataLookup: {
          locationType: "COLUMN",
          locationMatchingStrategy:
            "DEVELOPER_METADATA_LOCATION_MATCHING_STRATEGY_UNSPECIFIED",
          metadataKey: ELEMENT_ID_COLUMN_METADATA_KEY
        }
      }
    ]
  };
}

function insertBlankColumnsRequest(startIndex, count) {
  return {
    "insertDimension": {
      "range": {
        "dimension": "COLUMNS",
        "startIndex": startIndex,
        "endIndex": startIndex + count,
        "sheetId": 0
      },
      "inheritFromBefore": startIndex > 0
    }
  };
}

function updateColumnHeadersRequest(startIndex, headings) {
  return {
    "updateCells": {
      "start": {
        "sheetId": 0,
        "columnIndex": startIndex,
        "rowIndex": 0
      },
      "fields": "userEnteredValue",
      "rows": [{
        "values": headings.map(h => ({
          "userEnteredValue": {
            "stringValue": h
          }
        }))
      }]
    }
  };
}

function createColumnMetadataRequest(index, elementId) {
  return {
    "createDeveloperMetadata": {
      "developerMetadata": {
        "metadataKey": ELEMENT_ID_COLUMN_METADATA_KEY,
        "metadataValue": elementId,
        "visibility": "DOCUMENT",
        "location": {
          "dimensionRange": {
            "sheetId": 0,
            "startIndex": index,
            "endIndex": index + 1,
            "dimension": "COLUMNS"
          }
        }
      }
    }
  };
}

function insertBlankRowRequest() {
  return {
    "insertDimension": {
      "range": {
        "dimension": "ROWS",
        "startIndex": 1,
        "endIndex": 2,
        "sheetId": 0
      },
      "inheritFromBefore": false
    }
  };
}

function updateCellsRequest(values) {
  return {
    "updateCells": {
      "start": {
        "sheetId": 0,
        "columnIndex": 0,
        "rowIndex": 1
      },
      "fields": "userEnteredValue",
      "rows": [{
        "values": values.map(v => ({
          "userEnteredValue": {
              "stringValue": v
          }
        }))
      }]
    }
  };
}

function createRowMetadataRequest(recordId) {
  return {
    "createDeveloperMetadata": {
      "developerMetadata": {
        "metadataKey": RECORD_ID_ROW_METADATA_KEY,
        "metadataValue": recordId,
        "visibility": "DOCUMENT",
        "location": {
          "dimensionRange": {
            "sheetId": 0,
            "startIndex": 1,
            "endIndex": 2,
            "dimension": "ROWS"
          }
        }
      }
    }
  };
}

function updateRowRequest(recordId, values) {
  return {
    "valueInputOption": "USER_ENTERED",
    "data": [{
      "dataFilter": {
        "developerMetadataLookup": {
          "locationType": "ROW",
          "metadataKey": RECORD_ID_ROW_METADATA_KEY,
          "metadataValue": recordId
        }
      },
      "majorDimension": "ROWS",
      "values": [values]
    }]
  }
}
class GndSheets {
  constructor(auth, sheetId) {
    this.auth_ = auth;
    this.sheetId_ = sheetId;
  }

  authorizeRequest_(request) {
    return this.auth_.getAuthorizedClient().then(client => {
      return {
        spreadsheetId: this.sheetId_,
        auth: client,
        resource: request
      };
    }).catch(err => {
      console.error('Failed to authorize request: ', err);
    });
  }

  execute_(method, request) {
    return this.authorizeRequest_(request).then(authorizedRequest => {
      console.log("Sheets request:", JSON.stringify(authorizedRequest));
      return new Promise((resolve, reject) => {
        method(authorizedRequest, (err, response) => {
          if (err) {
            console.error('The API returned an error:', err);
            reject(err);
          } else {
            console.log("Sheets response:", JSON.stringify(response));
            resolve(response);
          }
        })
      });
    });
  }

  getColumnsByElementId() {
    return this.execute_(
      sheets.spreadsheets.developerMetadata.search,
      columnElementIdsRequest());
  }

  getColumnIds() {
    return this.getColumnsByElementId().then(resp => {
      if (!resp || !resp.matchedDeveloperMetadata) return [];
      let cols = [];
      const mds = resp.matchedDeveloperMetadata;
      mds.forEach(md => {
        const dmd = md.developerMetadata;
        cols[dmd.location.dimensionRange.startIndex] = 
          dmd.metadataValue;
      }); 
      return cols;
    });
  }

  batchUpdate_(requests) {
    return this.execute_(sheets.spreadsheets.batchUpdate, {requests});    
  }

  batchUpdateByDataFilter_(request) {
    return this.execute_(sheets.spreadsheets.values.batchUpdateByDataFilter,
      request);
  }

  updateColumns(existingColumns, formElements, startIndex) {
    const newColumns =
      formElements.filter(e => !existingColumns.includes(e.id));    
    const headings = newColumns.map(e => e.labels.pt);
    let cnt = 0;
    let requests = [];
    // Add columns for lat/lng in necessary.
    if (!existingColumns.includes(LAT_COLUMN_ID)) {
      // TODO: i18n.
      requests.push(insertBlankColumnsRequest(startIndex, 1));
      requests.push(updateColumnHeadersRequest(startIndex, ['Latitude']));
      requests.push(createColumnMetadataRequest(startIndex, LAT_COLUMN_ID));
      startIndex++;
      cnt++;
    }
    if (!existingColumns.includes(LNG_COLUMN_ID)) {
      // TODO: i18n.
      requests.push(insertBlankColumnsRequest(startIndex, 1));
      requests.push(updateColumnHeadersRequest(startIndex, ['Longitude']));
      requests.push(createColumnMetadataRequest(startIndex, LNG_COLUMN_ID));
      startIndex++;
      cnt++;
    }
    // Add columns for fields.
    requests.push(insertBlankColumnsRequest(startIndex, newColumns.length));
    requests.push(updateColumnHeadersRequest(startIndex, headings));
    for (let i = 0; i < newColumns.length; i++) {
      cnt++;
      requests.push(createColumnMetadataRequest(
        i + startIndex, newColumns[i].id));
    }
    return this.batchUpdate_(requests).then(() => cnt);
  }

  // TODO: merge with existing values instead of overwriting?
  getValues_(feature, record, colIds) {
    return colIds.map(
      id => {
        if (id == LAT_COLUMN_ID) {
          return String(feature.center[GEOPOINT_LAT]);
        }
        if (id == LNG_COLUMN_ID) {
          return String(feature.center[GEOPOINT_LNG]);
        }
        const values = record.responses[id];
        if (!values) {
          return '';
        }
        if (Array.isArray(values)) {
          return values.join(',');
        }
        return values;
      }
    ); 
  }

  addRow(feature, recordId, record, colIds) {    
    return this.batchUpdate_([
      insertBlankRowRequest(),
      updateCellsRequest(this.getValues_(feature, record, colIds)),
      createRowMetadataRequest(recordId)]);
  }

  updateRow(feature, recordId, record, colIds) {
    return this.batchUpdateByDataFilter_(
      updateRowRequest(recordId, this.getValues_(feature, record, colIds))
    );
  }
}

module.exports = GndSheets;