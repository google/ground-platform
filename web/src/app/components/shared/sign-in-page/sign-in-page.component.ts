/**
 * Copyright 2020 The Ground Authors.
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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { FirebaseUISignInFailure } from 'firebaseui-angular';
import { Observable, Subscription, filter } from 'rxjs';

import { AuthService } from 'app/services/auth/auth.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { environment } from 'environments/environment';

@Component({
    templateUrl: './sign-in-page.component.html',
    styleUrls: ['./sign-in-page.component.scss'],
    standalone: false
})
export class SignInPageComponent implements OnInit, OnDestroy {
  private subscription = new Subscription();
  constructor(
    private authService: AuthService,
    private navigationService: NavigationService
  ) {}

  ngOnInit() {
    this.subscription.add(
      // TODO(#545): Redirect to original URL on success.
      this.authService
        .isAuthenticated$()
        .pipe(filter(isAuth => isAuth || !!environment.useEmulators))
        .subscribe(() => this.navigationService.navigateToTermsOfService())
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  isAuthenticated$(): Observable<boolean> {
    return this.authService.isAuthenticated$();
  }

  errorCallback(errorData: FirebaseUISignInFailure) {
    // TODO: React to error.
    alert(`Sign in error ${errorData.code}`);
  }
}
