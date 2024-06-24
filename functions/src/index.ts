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
import {onHttpsRequest, onHttpsRequest2} from './handlers';
import {handleProfileRefresh} from './profile-refresh';
import {sessionLoginHandler} from './session-login';
import {importGeoJsonCallback} from './import-geojson';
import {exportCsvHandler} from './export-csv';
import {onCall} from 'firebase-functions/v2/https';
import {onWriteSubmissionHandler} from './on-write-submission';
import {onCreateLoiHandler} from './on-create-loi';
import {onWriteLoiHandler} from './on-write-loi';
import {onWriteSurveyHandler} from './on-write-survey';
import {loi, submission, survey} from './common/datastore';

/** Template for LOI write triggers capturing survey and LOI ids. */
export const loiPathTemplate = loi('{surveyId}', '{loiId}');

/** Template for submission write triggers capturing survey and submission ids. */
export const submissionPathTemplate = submission(
  '{surveyId}',
  '{submissionId}'
);

/** Template for survey write triggers capturing survey id. */
export const surveyPathTemplate = survey('{surveyId}');

export const profile = {
  refresh: onCall(request => handleProfileRefresh(request)),
};

export const importGeoJson = onHttpsRequest2(importGeoJsonCallback);

export const exportCsv = onHttpsRequest(exportCsvHandler);

export const onWriteSurvey = functions.firestore
  .document(surveyPathTemplate)
  .onWrite(onWriteSurveyHandler);

export const onWriteLoi = functions.firestore
  .document(loiPathTemplate)
  .onWrite(onWriteLoiHandler);

export const onCreateLoi = functions.firestore
  .document(loiPathTemplate)
  .onCreate(onCreateLoiHandler);

export const onWriteSubmission = functions.firestore
  .document(submissionPathTemplate)
  .onWrite(onWriteSubmissionHandler);

export const sessionLogin = onHttpsRequest(sessionLoginHandler);
