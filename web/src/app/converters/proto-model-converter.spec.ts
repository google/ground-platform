/**
 * Copyright 2024 The Ground Authors.
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

import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import { surveyToDocument } from './proto-model-converter';

import Pb = GroundProtos.ground.v1beta1;

const s = registry.getFieldIds(Pb.Survey);
const ai = registry.getFieldIds(Pb.AuditInfo);

describe('surveyToDocument()', () => {
  const auditInfo = new Pb.AuditInfo({
    userId: 'user1',
    emailAddress: 'user1@example.com',
    clientTimestamp: new GroundProtos.google.protobuf.Timestamp({
      seconds: 100,
    }),
  });

  it('omits audit info unless given', () => {
    const doc = surveyToDocument('survey1', { title: 'Survey' });

    expect(s['created'] in doc).toBeFalse();
    expect(s['lastModified'] in doc).toBeFalse();
  });

  it('writes the given created and lastModified audit info', () => {
    const doc = surveyToDocument(
      'survey1',
      { title: 'Survey' },
      { created: auditInfo, lastModified: auditInfo }
    );

    for (const field of [s['created'], s['lastModified']]) {
      expect(doc[field][ai['userId']]).toBe('user1');
      expect(doc[field][ai['emailAddress']]).toBe('user1@example.com');
    }
  });

  it('writes only lastModified when created is not given', () => {
    const doc = surveyToDocument(
      'survey1',
      { title: 'Survey' },
      { lastModified: auditInfo }
    );

    expect(s['created'] in doc).toBeFalse();
    expect(doc[s['lastModified']][ai['userId']]).toBe('user1');
  });
});
