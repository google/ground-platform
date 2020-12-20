/**
 * Copyright 2020 Google LLC
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

import { Component } from '@angular/core';
import {
  FirebaseUISignInFailure,
  FirebaseUISignInSuccessWithAuthResult,
} from 'firebaseui-angular';
import { Router } from '@angular/router';

@Component({
  templateUrl: './sign-in-page.component.html',
  styleUrls: ['./sign-in-page.component.css'],
})
export class SignInPageComponent {
  constructor(private router: Router) {}

  successCallback(signInSuccessData: FirebaseUISignInSuccessWithAuthResult) {
    // TODO(#545): Redirect to original URL on success.
    this.router.navigate(['/p/:new']);
  }

  errorCallback(errorData: FirebaseUISignInFailure) {
    // TODO: React to error.
    alert(`Sign in error ${errorData.code}`);
  }

  uiShownCallback() {
    // TODO: Disable buttons while signing in.
  }
}
