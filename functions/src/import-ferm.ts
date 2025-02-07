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
import {GroundProtos} from '@ground/proto';
import {ErrorHandler} from './handlers';

import {ground} from '@ground/proto/dist/ground-protos';
import {getDatastore} from './common/context';

import Pb = GroundProtos.ground.v1beta1;

type RequestBody = {
  id?: string;
  name: string;
  owner: string;
  collectors: [string];
};

export async function importFermCallback(
  req: functions.https.Request,
  res: functions.Response<any>,
  user: DecodedIdToken,
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
    // const jsonData = req.body as RequestBody;
    const jsonData = {
      id: '',
      name: '',
      owner: '',
      collectors: [''],
    } as RequestBody;

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
    const {name: surveyName, owner: ownerId, collectors} = jsonData;
    if (!surveyName) {
      return error(HttpStatus.BAD_REQUEST, 'Missing required field: name');
    }
    if (!ownerId) {
      return error(HttpStatus.BAD_REQUEST, 'Missing required field: owner');
    }

    // 4. Check if the owner exists
    const owner = await db.fetchUser(ownerId || '');
    if (!owner) {
      return error(HttpStatus.BAD_REQUEST, `Owner ${ownerId} doesn't exist`);
    }

    // 5. Survey creation
    const surveyId = '';

    const surveyPb = new Pb.Survey({
      id: surveyId,
      name: surveyName,
      description: 'Survey imported from FERM',
      acl: {
        '': ground.v1beta1.Role.SURVEY_ORGANIZER,
        '': ground.v1beta1.Role.DATA_COLLECTOR,
      },
      ownerId: '',
      state: Pb.Survey.State.READY,
    });

    // 5. Job creation
    const jobPb = new Pb.Job({id: '', name: ''});

    // 6. Send a success response:
    res
      .status(HttpStatus.OK)
      .json({message: 'Data imported successfully', data: jsonData}); // Include the data in the response (optional but good practice)

    done(); // Call done if you are using a framework that requires it (e.g., Express)
  } catch (e) {
    console.error('Error processing request:', e); // Log the actual error for debugging
    return error(HttpStatus.INTERNAL_SERVER_ERROR, 'Error processing request'); // Return a generic error message to the client
  }
}
