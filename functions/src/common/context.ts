/**
 * Copyright 2019 The Ground Authors.
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

import {Datastore} from './datastore';
import {MailService} from './mail-service';
import {initializeApp, getApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';

let datastore: Datastore | undefined;
let mailService: MailService | undefined;

export function initializeFirebaseApp() {
  try {
    getApp();
  } catch (e) {
    initializeApp();
  }
}

export function getDatastore(): Datastore {
  if (datastore) return datastore;
  initializeFirebaseApp();
  return new Datastore(getFirestore());
}

export async function getMailService(): Promise<MailService | undefined> {
  if (mailService) return mailService;
  const config = await MailService.getMailServerConfig(getDatastore());
  return new MailService(config);
}

export function resetDatastore() {
  datastore = undefined;
}
