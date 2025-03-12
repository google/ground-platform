/**
 * Copyright 2018 The Ground Authors.
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

import 'module-alias/register';
import * as functions from 'firebase-functions';
import {onHttpsRequest, onHttpsRequestAsync} from './handlers';
import {handleProfileRefresh} from './profile-refresh';
import {sessionLoginHandler} from './session-login';
import {importGeoJsonCallback} from './import-geojson';
import {importFermCallback} from './import-ferm';
import {exportCsvHandler} from './export-csv';
import {exportFermHandler} from './export-ferm';
import {exportGeojsonHandler} from './export-geojson';
import {onCall} from 'firebase-functions/v2/https';
import {onCreateLoiHandler} from './on-create-loi';
import {onCreatePasslistEntryHandler} from './on-create-passlist-entry';
import {onWriteJobHandler} from './on-write-job';
import {onWriteLoiHandler} from './on-write-loi';
import {onWriteSubmissionHandler} from './on-write-submission';
import {onWriteSurveyHandler} from './on-write-survey';
import {job, loi, passlistEntry, submission, survey} from './common/datastore';
import {initializeFirebaseApp} from './common/context';

// Ensure Firebase is initialized.
initializeFirebaseApp();

/** Template for passlist entry write triggers capturing passlist entry id. */
const passlistEntryPathTemplate = passlistEntry('{entryId}');

/** Template for job write triggers capturing survey and job id. */
const jobPathTemplate = job('{surveyId}', '{jobId}');

/** Template for LOI write triggers capturing survey and LOI id. */
const loiPathTemplate = loi('{surveyId}', '{loiId}');

/** Template for submission write triggers capturing survey and submission id. */
const submissionPathTemplate = submission('{surveyId}', '{submissionId}');

/** Template for survey write triggers capturing survey id. */
const surveyPathTemplate = survey('{surveyId}');

export const profile = {
  refresh: onCall(request => handleProfileRefresh(request)),
};

export const onCreatePasslistEntry = functions.firestore
  .document(passlistEntryPathTemplate)
  .onCreate(onCreatePasslistEntryHandler);

export const importGeoJson = onHttpsRequestAsync(importGeoJsonCallback);

export const importFerm = onHttpsRequestAsync(importFermCallback);

export const exportFerm = onHttpsRequest(exportFermHandler);

export const exportCsv = onHttpsRequest(exportCsvHandler);

export const exportGeojson = onHttpsRequest(exportGeojsonHandler);

export const onCreateLoi = functions.firestore
  .document(loiPathTemplate)
  .onCreate(onCreateLoiHandler);

export const onWriteJob = functions.firestore
  .document(jobPathTemplate)
  .onWrite(onWriteJobHandler);

export const onWriteLoi = functions.firestore
  .document(loiPathTemplate)
  .onWrite(onWriteLoiHandler);

export const onWriteSubmission = functions.firestore
  .document(submissionPathTemplate)
  .onWrite(onWriteSubmissionHandler);

export const onWriteSurvey = functions.firestore
  .document(surveyPathTemplate)
  .onWrite(onWriteSurveyHandler);

export const sessionLogin = onHttpsRequest(sessionLoginHandler);
