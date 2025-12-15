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
import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, RouterStateSnapshot} from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {User} from 'app/models/user.model';
import {AuthService} from 'app/services/auth/auth.service';
import {
  SIGN_IN_SEGMENT,
  TERMS,
} from 'app/services/navigation/navigation.constants';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {environment} from 'environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard {
  constructor(
    private authService: AuthService,
    private navigationService: NavigationService
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> {
    return this.authService.getUser$().pipe(
      map(user => this.canUserActivate(user, state.url)),
      catchError(() => {
        this.navigationService.signIn();
        return of(false);
      })
    );
  }

  canUserActivate(user: User, url: string): boolean {
    if (environment.useEmulators) {
      return true;
    }
    if (url.includes(SIGN_IN_SEGMENT)) {
      if (!user.isAuthenticated) {
        return true;
      }
      this.navigationService.navigateToSurveyList();
      return false;
    }

    if (url.includes(TERMS)) {
      if (user.isAuthenticated) {
        return true;
      }
      this.navigationService.signIn();
      return false;
    }

    if (user.isAuthenticated) {
      return true;
    }
    this.navigationService.signIn();
    return false;
  }
}
