/**
 * Copyright 2019 Google LLC
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

import { DataStoreService } from '../data-store/data-store.service';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { Injectable } from '@angular/core';
import firebase from 'firebase/app';
import { AngularFireAuth } from '@angular/fire/auth';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';
import { shareReplay } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

const ANONYMOUS_USER: User = {
  id: '',
  email: 'nobody',
  displayName: 'Anonymous user',
  isAuthenticated: false,
};
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  user$: Observable<User>;
  constructor(
    private afAuth: AngularFireAuth,
    private router: Router,
    dataStore: DataStoreService
  ) {
    this.user$ = this.afAuth.authState.pipe(
      switchMap(user => {
        if (user) {
          return dataStore.user$(user.uid);
        } else if (environment.useEmulators) {
          return of({ ...ANONYMOUS_USER, isAuthenticated: true });
        } else {
          return of(null);
        }
      }),
      map(
        user =>
          user || {
            ...ANONYMOUS_USER,
            isAuthenticated: environment.useEmulators,
          }
      ),
      // Cache last authenticated user so that late subscribers will receive
      // it as well.
      shareReplay(1)
    );
  }

  getUser$(): Observable<User> {
    return this.user$;
  }

  async isAuthenticated(): Promise<boolean> {
    return !!this.afAuth.currentUser;
  }

  async signIn() {
    const provider = new firebase.auth.GoogleAuthProvider();
    await this.afAuth.signInWithPopup(provider);
  }

  async signOut() {
    await this.afAuth.signOut();
    return this.router.navigate(['/']);
  }
}
