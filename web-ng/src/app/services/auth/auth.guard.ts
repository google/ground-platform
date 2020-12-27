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
import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService } from './auth.service';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { User } from '../../shared/models/user.model';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(private router: Router, private authService: AuthService) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> {
    return this.authService.getUser$().pipe(
      map(user => this.canUserActivate(user, state.url)),
      catchError(() => {
        this.router.navigate([AuthService.SIGN_IN_URL]);
        return of(false);
      })
    );
  }
  canUserActivate(user: User, url: string): boolean {
    if (environment.useEmulators) {
      return true;
    }
    if (url.includes(AuthService.SIGN_IN_URL)) {
      if (!user.isAuthenticated) {
        return true;
      }
      this.router.navigate(AuthService.DEFAULT_ROUTE);
      return false;
    }
    if (user.isAuthenticated) {
      return true;
    }
    this.router.navigate([AuthService.SIGN_IN_URL]);
    return false;
  }
}
