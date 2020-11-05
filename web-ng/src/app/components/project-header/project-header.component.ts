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

import { Component, ElementRef, Input, OnChanges } from '@angular/core';
import { AuthService } from './../../services/auth/auth.service';
import { UserProfilePopupComponent } from '../../components/user-profile-popup/user-profile-popup.component';
import { MatDialog } from '@angular/material/dialog';
import { Project } from './../../shared/models/project.model';
import { ProjectService } from '../../services/project/project.service';
import { Router } from '@angular/router';

@Component({
  selector: 'ground-project-header',
  templateUrl: './project-header.component.html',
  styleUrls: ['./project-header.component.scss'],
})
export class ProjectHeaderComponent implements OnChanges {
  @Input() project!: Project;
  lang: string;
  title!: string;
  projectId!: string;

  constructor(
    public auth: AuthService,
    private dialog: MatDialog,
    private projectService: ProjectService,
    private router: Router
  ) {
    this.lang = 'en';
  }

  ngOnChanges(): void {
    this.projectId = this.project.id;
    this.title = this.project.title.get(this.lang) || '';
  }

  openProfileDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog.open(UserProfilePopupComponent, {
      data: { trigger: target },
    });
  }

  /**
   * Updates the project title with input element value.
   *
   * @param evt the event emitted from the input element on blur.
   */
  updateProjectTitle(value: string) {
    if (!this.projectId) {
      return this.createProject(value);
    }
    if (value === this.title) return Promise.resolve();
    return this.projectService.updateTitle(this.projectId, value);
  }

  createProject(title: string) {
    this.projectService
      .createProject(title)
      .then(projectId => {
        this.router.navigateByUrl(`/p/${projectId}`);
      })
      .catch(e => {
        console.warn('Project creation failed', e);
      });
  }
}
