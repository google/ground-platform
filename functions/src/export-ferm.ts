/**
 * Copyright 2025 The Ground Authors.
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
import JSONStream from 'jsonstream-ts';
import {getDatastore} from './common/context';
import * as HttpStatus from 'http-status-codes';
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {toGeoJsonGeometry} from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;
import {getValue} from './export-csv';

export async function exportFermHandler(
  req: functions.Request,
  res: functions.Response<any>
) {
  try {
    const db = getDatastore();

    const surveyId = req.query.survey as string;
    const surveyDoc = await db.fetchSurvey(surveyId);
    if (!surveyDoc.exists) {
      res.status(HttpStatus.NOT_FOUND).send('Survey not found');
      return;
    }

    const jobDocs = await db.fetchJobs(surveyId);
    if (jobDocs.empty) {
      res.status(HttpStatus.NOT_FOUND).send('Job not found');
      return;
    }
    const jobDoc = jobDocs.docs.at(0);
    if (!jobDoc) {
      res.status(HttpStatus.NOT_FOUND).send('Job not found');
      return;
    }
    const job = toMessage(jobDoc.data(), Pb.Job);
    if (job instanceof Error) {
      res
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .send('Unsupported or corrupt job');
      return;
    }
    const {id: jobId} = job;

    res.type('application/json');
    const jsonStream = JSONStream.stringify('[', ',\n', ']', '  ');
    jsonStream.pipe(res);

    const loiDocs = await db.fetchLocationsOfInterest(surveyId, jobId);

    await Promise.all(
      loiDocs.docs.map(async loiDoc => {
        const loi = toMessage(loiDoc.data(), Pb.LocationOfInterest);
        if (loi instanceof Error) return;
        if (!loi.geometry) return;
        const geoJsonGeometry = toGeoJsonGeometry(loi.geometry);
        const qs = await db.fetchSubmissions(surveyId, loi.id);
        const submissionDoc = qs.docs.at(qs.size - 1);
        if (!submissionDoc) return;
        const submission = toMessage(submissionDoc.data(), Pb.Submission);
        if (submission instanceof Error) return;
        const {taskData: data} = submission;
        const ecosystems: (string | number | null)[] = [];
        job.tasks.forEach(task => ecosystems.push(getValue(task, data)));
        const row = {
          geometry: geoJsonGeometry,
          ecosystems,
        };
        jsonStream.write(row);
      })
    );

    res.status(HttpStatus.OK);
    jsonStream.end();
  } catch (e) {
    console.error('Error processing request:', e);
    res
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .send('Error processing request');
    return;
  }
}
