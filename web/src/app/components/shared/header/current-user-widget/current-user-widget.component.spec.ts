/**
 * Copyright 2022 The Ground Authors.
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';

import { CurrentUserWidgetComponent } from 'app/components/shared/header/current-user-widget/current-user-widget.component';
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';

describe('CurrentUserWidgetComponent', () => {
  let component: CurrentUserWidgetComponent;
  let fixture: ComponentFixture<CurrentUserWidgetComponent>;
  const user$ = new Subject<User | null>();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user$, getUser$: () => user$ } },
        { provide: MatDialog, useValue: {} },
      ],
      declarations: [CurrentUserWidgetComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CurrentUserWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
