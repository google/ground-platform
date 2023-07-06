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

import 'module-alias/register';
import * as functions from 'firebase-functions';
import {onHttpsRequest} from "@/handlers";
import {handleCreateUser} from '@/on-create-user';
import {importCsvHandler} from '@/import-csv';
import {importGeoJsonHandler} from '@/import-geojson';
import {exportCsvHandler} from '@/export-csv';
import {
  onWriteSurveyHandler,
  surveyPathTemplate,
  loiPathTemplate,
} from '@/on-write-survey';

// Create user profile in database when user first logs in.
export const onCreateUser = functions.auth.user().onCreate(handleCreateUser);

export const importCsv = onHttpsRequest(importCsvHandler);

export const importGeoJson = onHttpsRequest(importGeoJsonHandler);

export const exportCsv = onHttpsRequest(exportCsvHandler);

export const onWriteSurvey = functions.firestore
  .document(surveyPathTemplate)
  .onWrite(onWriteSurveyHandler);

export const onWriteLoi = functions.firestore
  .document(loiPathTemplate)
  .onWrite(onWriteSurveyHandler);
