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

/**
 * @author gmiceli@google.com (Gino Miceli)
 */ 

'use strict';

const functions = require('firebase-functions');
const serviceAccount = require('./service-account-secret.json');
const {JWT} = require('google-auth-library');

const API_SCOPES = ['https://www.googleapis.com/auth/spreadsheets'];

class GndAuth {
  constructor() {
    this.jwtClient_ = null;
  }

  getAuthorizedClient() {
    if (!this.jwtClient_) {
      this.jwtClient_ = new JWT({
        email: serviceAccount['client_email'],
        key: serviceAccount['private_key'],
        scopes: API_SCOPES
      });
    }
    return Promise.resolve(this.jwtClient_);
  }
}

module.exports = GndAuth;