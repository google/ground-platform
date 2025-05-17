/**
 * Copyright 2024 The Ground Authors.
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

import * as functions from 'firebase-functions';
import {canExport} from './common/auth';
import {getDatastore} from './common/context';
import * as HttpStatus from 'http-status-codes';
import {DecodedIdToken} from 'firebase-admin/auth';
import {registry} from '@ground/lib';
import {GroundProtos} from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
const s = registry.getFieldIds(Pb.Survey);
const j = registry.getFieldIds(Pb.Job);

export async function cloneSurveyHandler(
  req: functions.Request,
  res: functions.Response<any>,
  user: DecodedIdToken
) {
  const db = getDatastore();
  const surveyId = req.query.survey as string;

  const surveyDoc = await db.fetchSurvey(surveyId);
  if (!surveyDoc.exists) {
    res.status(HttpStatus.NOT_FOUND).send('Survey not found');
    return;
  }
  if (!canExport(user, surveyDoc)) {
    res.status(HttpStatus.FORBIDDEN).send('Permission denied');
    return;
  }

  const newSurveyId = db.generateId();

  const surveyData = surveyDoc.data();
  if (surveyData) {
    surveyData[s.id] = surveyId;
    surveyData[s.name] = 'Copy of ' + surveyData[s.name];

    db.insertSurvey(newSurveyId, surveyData);

    const jobDocs = await db.fetchJobs(surveyId);

    if (jobDocs.size > 0) {
      jobDocs.docs.forEach(doc => {
        const jobData = doc.data();

        db.insertJob(newSurveyId, jobData[j.id], jobData);
      });
    }
  }

  res.status(HttpStatus.OK);
  res.end(newSurveyId);
}
