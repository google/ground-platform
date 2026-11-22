/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
  createMockFirestore,
  stubAdminApi,
} from '@ground/lib/testing/firestore';
import { createGetRequestSpy } from './testing/http-test-helpers';
import { DecodedIdToken } from 'firebase-admin/auth';
import { Firestore } from 'firebase-admin/firestore';
import { PassThrough, Readable } from 'stream';
import type { Response } from 'express';
import { DATA_COLLECTOR_ROLE, SURVEY_ORGANIZER_ROLE } from './common/auth';
import * as context from './common/context';
import { resetDatastore } from './common/context';
import { exportMediaHandler } from './export-media';
import { StatusCodes } from 'http-status-codes';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
const sv = registry.getFieldIds(Pb.Survey);
const s = registry.getFieldIds(Pb.Submission);
const d = registry.getFieldIds(Pb.TaskData);

/** Records what a handler wrote to the response it was given. */
interface FakeResponse {
  res: Response;
  bytes: () => Buffer;
  statusCode: () => number;
  header: (name: string) => string | undefined;
}

/**
 * Builds a response which is a real writable stream, so that the handler can
 * pipe photo bytes into it, while still recording status and headers.
 */
function createStreamingResponseSpy(): FakeResponse {
  const stream = new PassThrough();
  const chunks: Buffer[] = [];
  stream.on('data', (c: Buffer) => chunks.push(c));
  const state = {
    statusCode: StatusCodes.OK as number,
    headers: {} as Record<string, string>,
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const res = stream as any;
  res.headersSent = false;
  res.setHeader = (name: string, value: unknown) => {
    state.headers[name.toLowerCase()] = String(value);
    return res;
  };
  res.status = (code: number) => {
    state.statusCode = code;
    return res;
  };
  res.send = () => res;
  return {
    res: res as Response,
    bytes: () => Buffer.concat(chunks),
    statusCode: () => state.statusCode,
    header: name => state.headers[name],
  };
}

describe('exportMedia()', () => {
  const SURVEY_ID = 'survey001';
  const SUBMISSION_ID = 'subm001';
  const TASK_ID = 'task006';
  const PHOTO_PATH = `user-media/surveys/${SURVEY_ID}/submissions/${TASK_ID}-4f8b1c2d-9a3e-4d71-b0c5-2e6a7f9d1834.jpg`;
  const PHOTO_BYTES = Buffer.from('fake-jpeg-bytes');

  const organizerEmail = 'organizer@test.it';
  const collectorEmail = 'collector@test.it';
  const collectorId = 'user5000';
  const otherUserId = 'user6000';

  let mockFirestore: Firestore;
  let mockFile: jasmine.SpyObj<{
    getMetadata: () => Promise<unknown[]>;
    createReadStream: () => Readable;
  }>;
  let mockBucket: jasmine.SpyObj<{ file: (path: string) => unknown }>;

  const organizer = {
    email: organizerEmail,
    uid: 'user1000',
  } as DecodedIdToken;
  const collector = {
    email: collectorEmail,
    uid: collectorId,
  } as DecodedIdToken;

  /** A survey restricted to its ACL, with the given data visibility. */
  function survey(dataVisibility?: number) {
    return {
      [sv.name]: 'Test survey',
      [sv.generalAccess]: Pb.Survey.GeneralAccess.RESTRICTED,
      [sv.acl]: {
        [organizerEmail]: SURVEY_ORGANIZER_ROLE,
        [collectorEmail]: DATA_COLLECTOR_ROLE,
      },
      ...(dataVisibility ? { [sv.dataVisibility]: dataVisibility } : {}),
    };
  }

  /** A submission whose task006 holds a photo at the given path. */
  function submission(ownerId: string, photoPath: string | null = PHOTO_PATH) {
    return {
      [s.id]: SUBMISSION_ID,
      [s.loiId]: 'loi001',
      [s.jobId]: 'job001',
      [s.ownerId]: ownerId,
      [s.taskData]: [
        {
          [d.id]: 'data001',
          [d.taskId]: 'task001',
          [d.textResponse]: { '1': 'no photo here' },
        },
        {
          [d.id]: 'data006',
          [d.taskId]: TASK_ID,
          ...(photoPath
            ? { [d.takePhotoResult]: { '1': photoPath } }
            : { [d.textResponse]: { '1': 'not a photo' } }),
        },
      ],
    };
  }

  async function request(query: Record<string, string>) {
    return await createGetRequestSpy({ url: '/exportMedia', query });
  }

  const validQuery = {
    survey: SURVEY_ID,
    submission: SUBMISSION_ID,
    task: TASK_ID,
  };

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
    mockFile = jasmine.createSpyObj('file', [
      'getMetadata',
      'createReadStream',
    ]);
    mockFile.getMetadata.and.resolveTo([
      { contentType: 'image/jpeg', size: PHOTO_BYTES.length },
    ]);
    mockFile.createReadStream.and.callFake(() => Readable.from([PHOTO_BYTES]));
    mockBucket = jasmine.createSpyObj('bucket', ['file']);
    mockBucket.file.and.returnValue(mockFile);
    spyOn(context, 'getStorageBucket').and.returnValue(mockBucket as any);
  });

  afterEach(() => {
    resetDatastore();
  });

  it('streams the photo to a survey organizer', async () => {
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId));
    const { res, bytes, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(mockBucket.file).toHaveBeenCalledWith(PHOTO_PATH);
    expect(bytes().toString()).toEqual(PHOTO_BYTES.toString());
    expect(statusCode()).toEqual(StatusCodes.OK);
  });

  it('sets content type, length, and a private cache policy', async () => {
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId));
    const { res, header } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(header('content-type')).toEqual('image/jpeg');
    expect(header('content-length')).toEqual(String(PHOTO_BYTES.length));
    expect(header('cache-control')).toContain('private');
  });

  it('rejects requests missing a parameter', async () => {
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(
      await request({ survey: SURVEY_ID, task: TASK_ID }),
      res,
      organizer
    );

    expect(statusCode()).toEqual(StatusCodes.BAD_REQUEST);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('returns not found for an unknown survey', async () => {
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('denies a user with no role in a restricted survey', async () => {
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId));
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, {
      email: 'stranger@test.it',
      uid: otherUserId,
    } as DecodedIdToken);

    expect(statusCode()).toEqual(StatusCodes.FORBIDDEN);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('returns not found for an unknown submission', async () => {
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it("hides another contributor's photo when data visibility is restricted", async () => {
    mockFirestore
      .doc(`surveys/${SURVEY_ID}`)
      .set(survey(Pb.Survey.DataVisibility.CONTRIBUTOR_AND_ORGANIZERS));
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(otherUserId));
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, collector);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('serves a contributor their own photo when data visibility is restricted', async () => {
    mockFirestore
      .doc(`surveys/${SURVEY_ID}`)
      .set(survey(Pb.Survey.DataVisibility.CONTRIBUTOR_AND_ORGANIZERS));
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId));
    const { res, bytes } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, collector);

    expect(bytes().toString()).toEqual(PHOTO_BYTES.toString());
  });

  it("serves another contributor's photo when all participants may view data", async () => {
    mockFirestore
      .doc(`surveys/${SURVEY_ID}`)
      .set(survey(Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS));
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(otherUserId));
    const { res, bytes } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, collector);

    expect(bytes().toString()).toEqual(PHOTO_BYTES.toString());
  });

  it('returns not found when the task holds no photo', async () => {
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId, null));
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('refuses a stored path pointing outside the survey', async () => {
    spyOn(console, 'warn');
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(
        submission(collectorId, 'user-media/surveys/other-survey/x/photo.jpg')
      );
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(context.getStorageBucket).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalled();
  });

  it('returns not found when the stored object is gone', async () => {
    spyOn(console, 'debug');
    mockFirestore.doc(`surveys/${SURVEY_ID}`).set(survey());
    mockFirestore
      .doc(`surveys/${SURVEY_ID}/submissions/${SUBMISSION_ID}`)
      .set(submission(collectorId));
    mockFile.getMetadata.and.rejectWith(new Error('No such object'));
    const { res, statusCode } = createStreamingResponseSpy();

    await exportMediaHandler(await request(validQuery), res, organizer);

    expect(statusCode()).toEqual(StatusCodes.NOT_FOUND);
    expect(mockFile.createReadStream).not.toHaveBeenCalled();
  });
});
