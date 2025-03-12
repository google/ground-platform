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

import functions from 'firebase-functions';
import HttpStatus from 'http-status-codes';
import {DecodedIdToken} from 'firebase-admin/auth';
import {toDocumentData} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {ErrorHandler} from './handlers';
import {ground} from '@ground/proto/dist/ground-protos';
import {getDatastore} from './common/context';

import Pb = GroundProtos.ground.v1beta1;

type RequestBody = {
  id: string;
  name: string;
  owner: string;
  collectors: [string];
};

export async function importFermCallback(
  req: functions.https.Request,
  res: functions.Response<any>,
  _: DecodedIdToken,
  done: () => void,
  error: ErrorHandler
) {
  if (req.method !== 'POST') {
    return error(
      HttpStatus.METHOD_NOT_ALLOWED,
      `Expected method POST, got ${req.method}`
    );
  }

  try {
    const db = getDatastore();

    // 1. Access the JSON payload:
    const jsonData = req.body as RequestBody;

    // 2. Validate the JSON (Important!):
    if (
      !jsonData ||
      typeof jsonData !== 'object' ||
      Object.keys(jsonData).length === 0
    ) {
      // Check for empty or non-object body
      return error(
        HttpStatus.BAD_REQUEST,
        'Invalid JSON payload: Request body is empty or not a valid JSON object.'
      );
    }

    // 3. Process the JSON data:
    console.log('Received JSON:', jsonData); // Log for debugging
    const {
      id: surveyId,
      name: surveyName,
      owner: ownerEmail,
      collectors,
    } = jsonData;
    if (!surveyId) {
      return error(HttpStatus.BAD_REQUEST, 'Missing required field: id');
    }
    if (!surveyName) {
      return error(HttpStatus.BAD_REQUEST, 'Missing required field: name');
    }
    if (!ownerEmail) {
      return error(HttpStatus.BAD_REQUEST, 'Missing required field: owner');
    }

    // 4. Check if the owner exists
    const ownerId = await db.getUserIdByEmail(ownerEmail || '');
    if (!ownerId) {
      return error(HttpStatus.BAD_REQUEST, `Owner ${ownerId} doesn't exist`);
    }
    console.log('OWNERID', ownerId);

    // 5. Survey creation
    const surveyPb = new Pb.Survey({
      id: surveyId,
      name: surveyName,
      description: 'Survey imported from FERM',
      acl: {
        [ownerEmail]: ground.v1beta1.Role.SURVEY_ORGANIZER,
        ...collectors.reduce<{[key: string]: ground.v1beta1.Role}>(
          (prev, curr) => {
            prev[curr] = ground.v1beta1.Role.DATA_COLLECTOR;
            return prev;
          },
          {}
        ),
      },
      ownerId,
      state: Pb.Survey.State.READY,
    });
    console.log('SURVEYID', surveyId);
    console.log('SURVEYPB', surveyPb);
    await db.insertSurvey(surveyId, toDocumentData(surveyPb));

    // 5. Job creation
    const jobId = await db.generateId();
    const taskPb = new Pb.Task({
      id: await db.generateId(),
      index: 0,
      prompt: 'Draw a polygon',
      required: true,
      drawGeometry: new Pb.Task.DrawGeometry({
        allowedMethods: [Pb.Task.DrawGeometry.Method.DRAW_AREA],
      }),
      level: Pb.Task.DataCollectionLevel.LOI_METADATA,
    });
    const tasksPb = await Promise.all([
      taskPb,
      ...realms.map(
        async (realm, index) =>
          new Pb.Task({
            id: await db.generateId(),
            index: index + 1,
            prompt: realm.label,
            required: false,
            multipleChoiceQuestion: new Pb.Task.MultipleChoiceQuestion({
              type: Pb.Task.MultipleChoiceQuestion.Type.SELECT_MULTIPLE,
              options: await Promise.all(
                realm.items.map(
                  async (item, index) =>
                    new Pb.Task.MultipleChoiceQuestion.Option({
                      id: await db.generateId(),
                      index,
                      label: item.label,
                    })
                )
              ),
            }),
          })
      ),
    ]);
    const jobPb = new Pb.Job({
      id: jobId,
      index: 0,
      name: 'Realms',
      tasks: tasksPb,
    });
    console.log('JOBID', jobId);
    console.log('JOBPB', jobPb);
    await db.insertJob(surveyId, jobId, toDocumentData(jobPb));

    // 6. Send a success response:
    res.status(HttpStatus.OK).json({
      message: 'Data imported successfully',
      data: {surveyId, url: `https://ground.openforis.org/${surveyId}`},
    });

    done();
  } catch (e) {
    console.error('Error processing request:', e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, 'Error processing request');
  }
}

const realms = [
  {
    label: 'T - Terrestrial realm',
    items: [
      {
        value: 'T1',
        label: 'T1 - Tropical-subtropical forests biome',
      },
      {
        value: 'T2',
        label: 'T2 - Temperate-boreal forests and woodlands biome',
      },
      {
        value: 'T3',
        label: 'T3 - Shrublands and shrubby woodlands biome',
      },
      {
        value: 'T4',
        label: 'T4 - Savannas and grasslands biome',
      },
      {
        value: 'T5',
        label: 'T5 - Deserts and semi-deserts biome',
      },
      {
        value: 'T6',
        label: 'T6 - Polar/alpine (cryogenic) biome',
      },
      {
        value: 'T7',
        label: 'T7 - Intensive land-use biome',
      },
    ],
  },
  {
    label: 'M - Marine realm',
    items: [
      {
        value: 'M1',
        label: 'M1 - Marine shelf biome',
      },
      {
        value: 'M2',
        label: 'M2 - Pelagic ocean waters biome',
      },
      {
        value: 'M3',
        label: 'M3 - Deep sea floors biome',
      },
      {
        value: 'M4',
        label: 'M4 - Anthropogenic marine biome',
      },
    ],
  },
  {
    label: 'F - Freshwater realm',
    items: [
      {
        value: 'F1',
        label: 'F1 - Rivers and streams biome',
      },
      {
        value: 'F2',
        label: 'F2 - Lakes biome',
      },
      {
        value: 'F3',
        label: 'F3 - Artificial wetlands biome',
      },
    ],
  },
  {
    label: 'S - Subterranean realm',
    items: [
      {
        value: 'S1',
        label: 'S1 - Subterranean lithic biome',
      },
      {
        value: 'S2',
        label: 'S2 - Anthropogenic subterranean voids biome',
      },
    ],
  },
  {
    label: 'MT - Marine-Terrestrial realm',
    items: [
      {
        value: 'MT1',
        label: 'MT1 - Shorelines biome',
      },
      {
        value: 'MT2',
        label: 'MT2 - Supralittoral coastal biome',
      },
      {
        value: 'MT3',
        label: 'MT3 - Anthropogenic shorelines biome',
      },
    ],
  },
  {
    label: 'SF - Subterranean-Freshwater realm',
    items: [
      {
        value: 'SF1',
        label: 'SF1 - Subterranean freshwaters biome',
      },
      {
        value: 'SF2',
        label: 'SF2 - Anthropogenic subterranean freshwaters biome',
      },
    ],
  },
  {
    label: 'FM - Freshwater-Marine realm',
    items: [
      {
        value: 'FM1',
        label: 'FM1 - Semi-confined transitional waters biome',
      },
    ],
  },
  {
    label: 'MFT - Marine-Freshwater-Terrestrial realm',
    items: [
      {
        value: 'MFT1',
        label: 'MFT1 - Brackish tidal biome',
      },
    ],
  },
  {
    label: 'SM - Subterranean-Marine realm',
    items: [
      {
        value: 'SM1',
        label: 'SM1 - Subterranean tidal biome',
      },
    ],
  },
  {
    label: 'TF - Terrestrial-Freshwater realm',
    items: [
      {
        value: 'TF1',
        label: 'TF1 - Palustrine wetlands biome',
      },
    ],
  },
];
