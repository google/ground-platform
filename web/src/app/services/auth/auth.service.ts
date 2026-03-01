/**
 * Copyright 2019 The Ground Authors.
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

import '@angular/localize/init';

import { Injectable, Injector, runInInjectionContext } from '@angular/core';
import {
  Auth,
  User as FirebaseUser,
  GoogleAuthProvider,
  authState,
  signInWithPopup,
  signOut,
} from '@angular/fire/auth';
import { Functions, httpsCallable } from '@angular/fire/functions';
import { Observable, Subject, firstValueFrom, from } from 'rxjs';
import { map, mergeWith, shareReplay, switchMap } from 'rxjs/operators';

import { AclEntry } from 'app/models/acl-entry.model';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import { Survey } from 'app/models/survey.model';
import { User } from 'app/models/user.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { environment } from 'environments/environment';

import { HttpClientService } from '../http-client/http-client.service';

const ANONYMOUS_USER: User = {
  id: '',
  email: 'nobody',
  displayName: 'Anonymous user',
  isAuthenticated: false,
};

/** Roles and labels for select drop-downs. */
export const ROLE_OPTIONS = [
  {
    label: $localize`:@@app.labels.roles.dataCollector:Data collector`,
    value: Role.DATA_COLLECTOR,
  },
  {
    label: $localize`:@@app.labels.roles.surveyOrganizer:Survey organizer`,
    value: Role.SURVEY_ORGANIZER,
  },
  {
    label: $localize`:@@app.labels.roles.viewer:Viewer`,
    value: Role.VIEWER,
  },
];

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private user$: Observable<User>;
  private tokenChanged$ = new Subject<FirebaseUser | null>();
  private currentUser!: User;
  private hasAcceptedTos = false;

  constructor(
    private auth: Auth,
    private dataStore: DataStoreService,
    private navigationService: NavigationService,
    private functions: Functions,
    private httpClientService: HttpClientService,
    private injector: Injector
  ) {
    // onIdTokenChanged via RxJS 'idToken' or 'user' specific helper?
    // @angular/fire/auth provides 'user' which wraps onIdTokenChanged.
    // However, existing code pipes it to tokenChanged$.
    // Let's use the 'user' observable from @angular/fire/auth directly if possible, or just hook up manually.
    // Typically `user(this.auth)` matches onIdTokenChanged.
    // Let's explicitly use the modular onIdTokenChanged function for now to replicate exact behavior if simpler.
    // actually, let's use the RxJS way:
    // import { user } from '@angular/fire/auth'; --> corresponds to onIdTokenChanged.
    // But I didn't import 'user' in the top block, I imported 'User as FirebaseUser'.
    // Let's just use the strict SDK method in constructor.
    this.auth.onIdTokenChanged(user => this.tokenChanged$.next(user));

    this.user$ = authState(this.auth).pipe(
      mergeWith(this.tokenChanged$),
      switchMap(user => from(this.onAuthStateChange(user))),
      map(user => user || ANONYMOUS_USER),
      // Cache last authenticated user so that late subscribers receive it as well.
      shareReplay(1)
    );
    this.user$.subscribe(user => (this.currentUser = user));
  }

  /**
   * Calls a server endpoint to create a session cookie which may be used for authenticating against
   * backend functions which require session auth. Namely, this is necessary for functions which
   * download arbitrarily large files (namely, "/exportCsv"). These functions can only be invoked via
   * HTTP GET, since browser do not allow streaming directly to disk via POST or other methods.
   */
  async createSessionCookie() {
    try {
      // TODO(#1159): Refactor access to Cloud Functions into new service.
      await this.httpClientService.postWithAuth(
        `${environment.cloudFunctionsUrl}/sessionLogin`,
        {}
      );
    } catch (err) {
      console.error(
        'Session login failed. Some features may be unavailable',
        err
      );
    }
  }

  private async onAuthStateChange(
    user: FirebaseUser | null
  ): Promise<User | undefined> {
    if (!user) {
      return undefined;
    }
    await this.callProfileRefresh();
    return firstValueFrom(this.dataStore.user$(user.uid));
  }

  async callProfileRefresh() {
    // TODO(#1159): Refactor access to Cloud Functions into new service.
    const refreshProfile = runInInjectionContext(this.injector, () =>
      httpsCallable(this.functions, 'profile-refresh')
    );
    const result = (await refreshProfile({})).data;
    if (result !== 'OK') {
      throw new Error('User profile could not be updated');
    }
  }

  getUser$(): Observable<User> {
    return this.user$;
  }

  async getUser(userId: string): Promise<User | undefined> {
    return firstValueFrom(this.dataStore.user$(userId));
  }

  async isPasslisted(): Promise<boolean> {
    const { email } = this.currentUser;

    return this.dataStore.isPasslisted(email);
  }

  isAuthenticated$(): Observable<boolean> {
    return this.getUser$().pipe(map(user => user.isAuthenticated));
  }

  getCurrentUser(): User {
    return this.currentUser;
  }

  getHasAcceptedTos(): boolean {
    return this.hasAcceptedTos;
  }

  approveTos(): void {
    this.hasAcceptedTos = true;
  }

  async signIn() {
    const provider = new GoogleAuthProvider();
    await runInInjectionContext(this.injector, () =>
      signInWithPopup(this.auth, provider)
    );
  }

  async signOut() {
    await runInInjectionContext(this.injector, () => signOut(this.auth));
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
        job.strategy !== DataCollectionStrategy.PREDEFINED)
    );
  }

  isManager(role: Role): boolean {
    return [Role.OWNER, Role.SURVEY_ORGANIZER].includes(role);
  }

  isContributor(role: Role): boolean {
    return [Role.DATA_COLLECTOR].includes(role);
  }
}
