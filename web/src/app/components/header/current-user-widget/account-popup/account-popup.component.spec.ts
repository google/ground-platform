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

import {AuthService} from 'app/services/auth/auth.service';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {AccountPopupComponent} from 'app/components/header/current-user-widget/account-popup/account-popup.component';
import {
  MatLegacyDialogRef as MatDialogRef,
  MAT_LEGACY_DIALOG_DATA as MAT_DIALOG_DATA,
} from '@angular/material/legacy-dialog';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {Router} from '@angular/router';
import {of} from 'rxjs';

describe('AccountPopupComponent', () => {
  let component: AccountPopupComponent;
  let fixture: ComponentFixture<AccountPopupComponent>;
  const dialogRef: Partial<MatDialogRef<AccountPopupComponent>> = {};

  beforeEach(waitForAsync(() => {
    const routerSpy = createRouterSpy();
    TestBed.configureTestingModule({
      declarations: [AccountPopupComponent],
      imports: [MatDialogModule],
      providers: [
        {provide: AuthService, useValue: {getUser$: () => of()}},
        {provide: MAT_DIALOG_DATA, useValue: {}},
        {provide: MatDialogRef, useValue: dialogRef},
        {provide: Router, useValue: routerSpy},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountPopupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
