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

import {
  createMockFirestore,
  newDocumentSnapshot,
  newEventContext,
  stubAdminApi,
} from '@ground/lib/dist/testing/firestore';
import { resetDatastore } from './common/context';
import { Firestore } from 'firebase-admin/firestore';
import * as functions from './index';
import * as context from './common/context';
import { MailService } from './common/mail-service';

const test = require('firebase-functions-test')();

describe('onCreatePasslistEntry()', () => {
  let mockFirestore: Firestore;
  let getMailServiceMock: any;
  let mailServiceMock: any;

  const serverConfig = {
    port: 5555,
  };
  const mail = {
    html: 'html',
    subject: 'subject',
    to: 'this_is_a_test@test.com',
  };

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);

    mailServiceMock = jasmine.createSpyObj('MailService', [
      'sendMail',
    ]) as jasmine.SpyObj<MailService>;

    getMailServiceMock = spyOn(context, 'getMailService').and.returnValue(
      mailServiceMock
    );
  });

  afterEach(() => {
    resetDatastore();
  });

  afterAll(() => {
    test.cleanup();
  });

  it('passlist notification email template exists', async () => {
    await mockFirestore.collection('passlists').add({});
    await test.wrap(functions.onCreatePasslistEntry)(newDocumentSnapshot({}));
    expect(getMailServiceMock).not.toHaveBeenCalled();
  });

  it('mail server config exists', async () => {
    const docRef = mockFirestore.doc('config/mail');
    docRef.set({ server: serverConfig });
    const docSnapshot = await docRef.get();
    const data = docSnapshot.data();
    expect(data).toEqual({ server: serverConfig });
    expect(docSnapshot.exists).toBe(true);
    expect(docSnapshot.id).toBe('mail');
  });

  it('sends email notification', async () => {
    mockFirestore.doc('config/mail').set({ server: serverConfig });
    mockFirestore.doc('config/mail/templates/passlisted').set(mail);
    mockFirestore.doc(`passlists/${mail.to}`).set({});
    await test.wrap(functions.onCreatePasslistEntry)(
      newDocumentSnapshot({}),
      newEventContext({ entryId: mail.to })
    );
    expect(getMailServiceMock).toHaveBeenCalled();
    expect(mailServiceMock.sendMail).toHaveBeenCalled();
    expect(mailServiceMock.sendMail).toHaveBeenCalledWith(mail);
  });
});
