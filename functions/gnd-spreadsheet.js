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

/**
 * @author gmiceli@google.com (Gino Miceli)
 */ 
 
 'use strict';

const google = require('googleapis');
const util = require('util');

const sheets = google.sheets('v4');

const ELEMENT_ID_COLUMN_METADATA_KEY = 'elementId';
const RECORD_ID_ROW_METADATA_KEY = 'recordId';
const LAT_COLUMN_ID = '_latColumn';
const LNG_COLUMN_ID = '_lngColumn';
const GEOPOINT_LAT = '_latitude';
const GEOPOINT_LNG = '_longitude';
const INSPECT_OPTS = {compact: true, depth: 5};

function columnElementIdsRequest() {
  return {
    "dataFilters": [
      {
        "developerMetadataLookup": {
          "locationType": "COLUMN",
          "locationMatchingStrategy":
            "DEVELOPER_METADATA_LOCATION_MATCHING_STRATEGY_UNSPECIFIED",
          "metadataKey": ELEMENT_ID_COLUMN_METADATA_KEY
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
        "values": values.map(v=> ({
          "userEnteredValue": {
            [v.type]: v.value
          }
        }))
      }]
    }
  };
}

function stringValue(value) {
  return {
    "value": value,
    "type": "stringValue"
  };
}

function numberValue(value) {
  return {
    "value": value,
    "type": "numberValue"
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
      "values": [values.map(v => v.value)]
    }]
  }
}

class GndSpreadsheet {
  constructor(auth, sheetId) {
    this.auth_ = auth;
    this.sheetId_ = sheetId;
  }

  getColumnMetadata() {
    return this.query_(
      sheets.spreadsheets.developerMetadata.search,
      columnElementIdsRequest()).then(colResp => {
        if (!colResp || !colResp.data) {
          return Promise.reject('Failed to read spreadsheet');
        }
        const cols = colResp.data.matchedDeveloperMetadata || [];
        return cols
          .map(col => col.developerMetadata)
          .sort((a, b) => 
            a.location.dimensionRange.startIndex
            - b.location.dimensionRange.startIndex);
    });
  }

  getColumnIds() {
    return this.getColumnMetadata().then(mds => {
      if (!mds) return [];
      let cols = [];
      mds.forEach(dmd => {
        cols[dmd.location.dimensionRange.startIndex] = 
          dmd.metadataValue;
      }); 
      return cols;
    });
  }

  updateColumns(existingColumns, formElements, startIndex) {
    const newColumns =
      formElements.filter(e => !existingColumns.includes(e.id));
    // TODO: Update existing column headings when label changes.
    // TODO(i18n).
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

  addRow(feature, recordId, record, colIds) {
    if (!feature) return Promise.reject('Feature not found');
    if (!record) return Promise.reject('Record not found');
    return this.batchUpdate_([
      insertBlankRowRequest(),
      updateCellsRequest(this.getValues_(feature, record, colIds)),
      createRowMetadataRequest(recordId)]);
  }

  updateRow(feature, recordId, record, colIds) {
    if (!feature) return Promise.reject('Feature not found');
    if (!record) return Promise.reject('Record not found');
    return this.batchUpdateByDataFilter_(
      updateRowRequest(recordId, this.getValues_(feature, record, colIds))
    );
  }

  authorizeRequest_(request) {
    return this.auth_.getAuthorizedClient().then(client => {
      return {
        spreadsheetId: this.sheetId_,
        auth: client,
        resource: request
      };
    }).catch(err => {
      console.error('Failed to authorize request:', err);
    });
  }

  execute_(method, request) {
    return this.query_(method, request)
      .then(_ => Promise.resolve());
  }

  query_(method, request) {
    return this.authorizeRequest_(request).then(authorizedRequest => {
      return new Promise((resolve, reject) => {
        method(authorizedRequest, (err, response) => {
          if (err) {
            console.error('Sheets API returned an error:', err,
              'Request:', util.inspect(authorizedRequest, INSPECT_OPTS),
              'Response:', util.inspect(response, INSPECT_OPTS));
            reject(err);
          } else {
            resolve(response);
          }
        })
      });
    });
  }

  batchUpdate_(requests) {
    return this.execute_(sheets.spreadsheets.batchUpdate, {requests});    
  }

  batchUpdateByDataFilter_(request) {
    return this.execute_(
      sheets.spreadsheets.values.batchUpdateByDataFilter,
      request);
  }

  // TODO: merge with existing values instead of overwriting?
  getValues_(feature, record, colIds) {
    return colIds.map(
      id => {
        if (id == LAT_COLUMN_ID) {
          return numberValue(feature.center 
            && feature.center[GEOPOINT_LAT]);
        }
        if (id == LNG_COLUMN_ID) {
          return numberValue(feature.center
            && feature.center[GEOPOINT_LNG]);
        }
        const values = record.responses[id];
        if (!values) {
          return stringValue('');
        }
        if (Array.isArray(values)) {
          return stringValue(values.join(','));
        }
        return stringValue(values);
      });
  }  
}

module.exports = GndSpreadsheet;