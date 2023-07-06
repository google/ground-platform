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

import {DataStoreService} from 'app/services/data-store/data-store.service';
import {Observable, from} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {User} from 'app/models/user.model';
import {Injectable} from '@angular/core';
import {GoogleAuthProvider} from 'firebase/auth';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {map} from 'rxjs/operators';
import {shareReplay} from 'rxjs/operators';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {AclEntry} from 'app/models/acl-entry.model';
import {Job} from 'app/models/job.model';
import {Survey} from 'app/models/survey.model';
import {Role} from 'app/models/role.model';
import firebase from 'firebase/compat/app';
import {firstValueFrom} from 'rxjs';
import {AngularFireFunctions} from '@angular/fire/compat/functions';

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
  private user$: Observable<User>;
  private currentUser!: User;

  constructor(
    private afAuth: AngularFireAuth,
    private dataStore: DataStoreService,
    private navigationService: NavigationService,
    private functions: AngularFireFunctions
  ) {
    this.user$ = this.afAuth.authState.pipe(
      switchMap(user => from(this.onAuthStateChange(user))),
      map(user => user || ANONYMOUS_USER),
      // Cache last authenticated user so that late subscribers receive it as well.
      shareReplay(1)
    );
    this.user$.subscribe(user => (this.currentUser = user));
  }

  private async onAuthStateChange(
    user: firebase.User | null
  ): Promise<User | undefined> {
    if (!user) {
      return undefined;
    }
    await this.callProfileRefresh();
    return await firstValueFrom(this.dataStore.user$(user.uid));
  }

  async callProfileRefresh() {
    const refreshProfile = this.functions.httpsCallable('profile-refresh');
    const result = await firstValueFrom(refreshProfile({}));
    if (result !== 'OK') {
      throw new Error('User profile could not be updated');
    }
  }

  getUser$(): Observable<User> {
    return this.user$;
  }

  isAuthenticated$(): Observable<boolean> {
    return this.getUser$().pipe(map(user => user.isAuthenticated));
  }

  getCurrentUser(): User {
    return this.currentUser;
  }

  async signIn() {
    const provider = new GoogleAuthProvider();
    await this.afAuth.signInWithPopup(provider);
  }

  async signOut() {
    await this.afAuth.signOut();
    return this.navigationService.signOut();
  }

  /**
   * Checks if a user has survey organizer or owner level permissions of the survey.
   */
  canManageSurvey(acl: AclEntry[]): boolean {
    const userEmail = this.currentUser?.email;
    return !!acl.find(entry => entry.email === userEmail && entry.isManager());
  }

  /**
   * Checks if a user can add points to a specific job.
   */
  canUserAddPointsToJob(survey: Survey, job: Job): boolean {
    const user = this.getCurrentUser();
    if (!user) {
      return false;
    }
    const userRole: Role = survey.acl.get(user.email)!;

    return (
      this.isManager(userRole) ||
      (this.isContributor(userRole) &&
        (job.dataCollectorsCanAdd?.includes('points') ?? false))
    );
  }

  isManager(role: Role): boolean {
    return [Role.OWNER, Role.SURVEY_ORGANIZER].includes(role);
  }

  isContributor(role: Role): boolean {
    return [Role.DATA_COLLECTOR].includes(role);
  }
}
