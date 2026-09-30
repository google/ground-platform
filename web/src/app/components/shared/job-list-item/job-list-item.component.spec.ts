/**
 * Copyright 2020 The Ground Authors.
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

import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { List, Map } from 'immutable';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { GroundIconModule } from 'app/modules/ground-icon.module';
import { AuthService } from 'app/services/auth/auth.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { UrlParams } from 'app/services/navigation/url-params';

import {
  JobListItemActionsType,
  JobListItemComponent,
} from './job-list-item.component';

describe('JobListItemComponent', () => {
  let fixture: ComponentFixture<JobListItemComponent>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let urlParamsSignal: WritableSignal<UrlParams>;
  let sidePanelExpanded: WritableSignal<boolean>;

  const job = new Job(
    /* id= */ 'job001',
    /* index= */ 0,
    /* color= */ '#fff',
    /* name= */ 'job 1'
  );

  const surveyId = 'survey1';

  function createLois(
    count: number,
    submissionCounts: number[] = []
  ): List<LocationOfInterest> {
    const lois: LocationOfInterest[] = [];
    for (let i = 0; i < count; i++) {
      lois.push(
        new LocationOfInterest(
          /* id= */ 'loi' + i,
          /* jobId= */ job.id,
          /* geometry= */ new Point(new Coordinate(1.23, 4.56)),
          /* properties= */ Map(),
          /* customId= */ '',
          /* predefined= */ true,
          /* submissionCount= */ submissionCounts[i] ?? 0
        )
      );
    }
    return List(lois);
  }

  function query<T extends Element>(selector: string): T | null {
    return fixture.nativeElement.querySelector(selector);
  }

  function queryAll<T extends Element>(selector: string): T[] {
    return Array.from(fixture.nativeElement.querySelectorAll(selector));
  }

  function setLois(lois: List<LocationOfInterest>) {
    fixture.componentRef.setInput('lois', lois);
    fixture.detectChanges();
  }

  function clickExpandToggle() {
    query<HTMLButtonElement>('.expand-toggle')!.click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'clearLocationOfInterestId',
        'getSidePanelExpanded',
        'getUrlParams',
        'selectLocationOfInterest',
      ]
    );

    urlParamsSignal = signal(new UrlParams(surveyId, null, null, null));
    sidePanelExpanded = signal(true);

    spyOn(LocationOfInterestService, 'getDisplayName').and.callFake(
      loi => `Site ${loi.id}`
    );
    navigationServiceSpy.getUrlParams.and.returnValue(urlParamsSignal);
    // Read through a signal, like the real service, so OnPush views update.
    navigationServiceSpy.getSidePanelExpanded.and.callFake(() =>
      sidePanelExpanded()
    );

    await TestBed.configureTestingModule({
      declarations: [JobListItemComponent],
      imports: [GroundIconModule, MatButtonModule, MatMenuModule],
      providers: [
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: AuthService, useValue: {} },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListItemComponent);
    fixture.componentRef.setInput('job', job);
    fixture.detectChanges();
  });

  it('should render the job', () => {
    expect(queryAll('.job-tree-node').length).toBe(1);
    expect(query('.job-name')!.textContent!.trim()).toBe('job 1');
  });

  it('should not show the expand toggle when the job has no lois', () => {
    expect(query('.expand-toggle')).toBeNull();
  });

  it('should not render lois until the job is expanded', () => {
    setLois(createLois(2));

    expect(queryAll('.loi-tree-node').length).toBe(0);
  });

  it('should render lois when the job is expanded', () => {
    setLois(createLois(2));

    clickExpandToggle();

    expect(queryAll('.loi-tree-node').length).toBe(2);
    expect(query('.expand-toggle')!.getAttribute('aria-expanded')).toBe('true');
  });

  it('should hide lois when the job is collapsed again', () => {
    setLois(createLois(2));
    clickExpandToggle();

    clickExpandToggle();

    expect(queryAll('.loi-tree-node').length).toBe(0);
  });

  it('should render lois added after the job was expanded', () => {
    setLois(createLois(1));
    clickExpandToggle();

    setLois(createLois(3));

    expect(queryAll('.loi-tree-node').length).toBe(3);
  });

  it('should render the name and submission count of each loi', () => {
    setLois(createLois(3, [0, 4, 12]));
    clickExpandToggle();

    const names = queryAll('.loi-name').map(e => e.textContent!.trim());
    const counts = queryAll('.loi-submission-count').map(e =>
      e.textContent!.trim()
    );
    expect(names).toEqual(['Site loi0', 'Site loi1', 'Site loi2']);
    expect(counts).toEqual(['0', '4', '12']);
  });

  it('should highlight the selected loi', () => {
    setLois(createLois(3));
    clickExpandToggle();

    urlParamsSignal.set(new UrlParams(surveyId, 'loi1', null, null));
    fixture.detectChanges();

    const selected = queryAll('.loi-tree-node').map(e =>
      e.classList.contains('tree-node-selected')
    );
    expect(selected).toEqual([false, true, false]);
  });

  it('should select the loi when it is clicked', () => {
    setLois(createLois(2));
    clickExpandToggle();

    queryAll<HTMLButtonElement>('.loi-tree-node')[1].click();

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(surveyId, 'loi1');
  });

  it('should close the loi when the back button is clicked', () => {
    fixture.componentRef.setInput('actionsType', JobListItemActionsType.BACK);
    fixture.detectChanges();

    query<HTMLButtonElement>('.job-actions button')!.click();

    expect(navigationServiceSpy.clearLocationOfInterestId).toHaveBeenCalled();
  });

  describe('when the side panel is collapsed', () => {
    beforeEach(() => {
      setLois(createLois(2));
      sidePanelExpanded.set(false);
      fixture.detectChanges();
    });

    it('should expand the job when its icon is clicked', () => {
      query<HTMLButtonElement>('.job-tree-node button')!.click();
      fixture.detectChanges();

      expect(queryAll('.loi-collapsed-toggle').length).toBe(2);
      expect(queryAll('.loi-name').length).toBe(0);
    });

    it('should label loi buttons with the loi name', () => {
      query<HTMLButtonElement>('.job-tree-node button')!.click();
      fixture.detectChanges();

      const labels = queryAll('.loi-collapsed-toggle').map(e =>
        e.getAttribute('aria-label')
      );
      expect(labels).toEqual(['Site loi0', 'Site loi1']);
    });
  });
});
