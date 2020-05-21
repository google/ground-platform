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

import { AuthService } from './../../services/auth/auth.service';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { AngularFireModule } from '@angular/fire';
import { AngularFireAuthModule } from '@angular/fire/auth';
import { AngularFirestoreModule } from '@angular/fire/firestore';
import { ProjectHeaderComponent } from './project-header.component';
import { environment } from '../../../environments/environment';
import { InlineEditTitleModule } from '../inline-edit-title/inline-edit-title.module';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialogModule } from '@angular/material/dialog';
import { UserProfilePopupComponent } from '../user-profile-popup/user-profile-popup.component';
describe('ProjectHeaderComponent', () => {
  let component: ProjectHeaderComponent;
  let fixture: ComponentFixture<ProjectHeaderComponent>;
  const dialogRef: Partial<MatDialogRef<UserProfilePopupComponent>> = {};
  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        InlineEditTitleModule,
        AngularFireModule.initializeApp(environment.firebaseConfig),
        AngularFireAuthModule,
        AngularFirestoreModule,
        MatIconModule,
        MatDialogModule,
      ],
      declarations: [ProjectHeaderComponent],
      providers: [
        { provide: AuthService, useValue: {} },
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProjectHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
