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
import { NEVER } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SignInPageComponent } from './sign-in-page.component';

describe('SignInPageComponent', () => {
  let component: SignInPageComponent;
  let fixture: ComponentFixture<SignInPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SignInPageComponent],
      providers: [
        { provide: Router, useValue: {} },
        {
          provide: AuthService,
          useValue: { getUser$: () => NEVER, isAuthenticated$: () => NEVER },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SignInPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // TODO(#559): Implement tests.
  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
