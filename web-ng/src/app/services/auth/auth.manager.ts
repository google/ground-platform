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
import { Injectable, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { take } from 'rxjs/operators';
import { AclEntry } from '../../shared/models/acl-entry.model';
import { Project } from '../../shared/models/project.model';
import { Role } from '../../shared/models/role.model';
import { User } from '../../shared/models/user.model';
import { ProjectService } from '../project/project.service';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root',
})
export class AuthManager implements OnDestroy {
  private subscription = new Subscription();
  acl?: AclEntry[];
  user?: User;
  constructor(
    private projectService: ProjectService,
    private authService: AuthService
  ) {
    this.subscription.add(
      this.projectService
        .getActiveProject$()
        .pipe(take(1))
        .subscribe(p => this.onProjectLoaded(p))
    );
    this.subscription.add(
      this.authService.getUser$().subscribe(user => {
        this.user = user;
      })
    );
  }

  private onProjectLoaded(project: Project): void {
    this.acl = this.projectService.getProjectAcl(project);
  }

  canManageProject() {
    const userEmail = this.user?.email;
    const acl = this.acl?.find(val => val.email === userEmail);
    if (!acl) {
      return false;
    }
    if (acl.role === Role.MANAGER || acl.role === Role.OWNER) {
      return true;
    }
    return false;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
