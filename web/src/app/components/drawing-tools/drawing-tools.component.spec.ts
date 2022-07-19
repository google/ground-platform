/**
 * Copyright 2021 Google LLC
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

import {
  ComponentFixture,
  fakeAsync,
  flush,
  TestBed,
  tick,
  waitForAsync,
} from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { ProjectService } from '../../services/project/project.service';
import { Job } from '../../shared/models/job.model';
import { Project } from '../../shared/models/project.model';
import { StringMap } from '../../shared/models/string-map.model';
import { DrawingToolsComponent } from './drawing-tools.component';
import { DrawingToolsModule } from './drawing-tools.module';
import { Map } from 'immutable';
import { AuthService } from '../../services/auth/auth.service';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { urlPrefix } from '../map/ground-pin';
import { By } from '@angular/platform-browser';

describe('DrawingToolsComponent', () => {
  let fixture: ComponentFixture<DrawingToolsComponent>;

  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let mockDisabled$: BehaviorSubject<boolean>;
  let mockEditMode$: BehaviorSubject<EditMode>;
  let drawingToolsServiceSpy: jasmine.SpyObj<DrawingToolsService>;
  let mockObservationId$: BehaviorSubject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;

  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const jobColor1 = 'red';
  const jobColor2 = 'green';
  const jobName1 = 'job001 name';
  const jobName2 = 'job002 name';
  const mockProject = new Project(
    'project001',
    StringMap({ en: 'title1' }),
    StringMap({ en: 'description1' }),
    /* jobs= */ Map({
      job001: new Job(
        jobId1,
        /* index */ -1,
        jobColor1,
        StringMap({ en: jobName1 }),
        /* forms= */ Map()
      ),
      job002: new Job(
        jobId2,
        /* index */ -1,
        jobColor2,
        StringMap({ en: jobName2 }),
        /* forms= */ Map()
      ),
    }),
    /* acl= */ Map()
  );

  beforeEach(
    waitForAsync(() => {
      authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
        'canUserAddPointsToJob',
      ]);
      authServiceSpy.canUserAddPointsToJob.and.returnValue(true);

      mockDisabled$ = new BehaviorSubject<boolean>(false);
      mockEditMode$ = new BehaviorSubject<EditMode>(EditMode.None);
      drawingToolsServiceSpy = jasmine.createSpyObj<DrawingToolsService>(
        'DrawingToolsService',
        ['getDisabled$', 'getEditMode$', 'setSelectedJobId', 'setEditMode']
      );
      drawingToolsServiceSpy.getDisabled$.and.returnValue(mockDisabled$);
      drawingToolsServiceSpy.getEditMode$.and.returnValue(mockEditMode$);

      navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
        'NavigationService',
        ['getObservationId$']
      );
      mockObservationId$ = new BehaviorSubject<string | null>(null);
      navigationServiceSpy.getObservationId$.and.returnValue(
        mockObservationId$
      );

      projectServiceSpy = jasmine.createSpyObj<ProjectService>(
        'ProjectService',
        ['getActiveProject$']
      );
      projectServiceSpy.getActiveProject$.and.returnValue(
        of<Project>(mockProject)
      );

      TestBed.configureTestingModule({
        imports: [DrawingToolsModule, BrowserAnimationsModule],
        declarations: [DrawingToolsComponent],
        providers: [
          { provide: AuthService, useValue: authServiceSpy },
          { provide: DrawingToolsService, useValue: drawingToolsServiceSpy },
          { provide: NavigationService, useValue: navigationServiceSpy },
          { provide: ProjectService, useValue: projectServiceSpy },
        ],
      }).compileComponents();
    })
  );

  function resetFixture() {
    fixture = TestBed.createComponent(DrawingToolsComponent);
    fixture.detectChanges();
  }

  function assertElementSrcColor(element: Element, color: string) {
    expect(
      atob(element.getAttribute('src')!.slice(urlPrefix.length))
    ).toContain(color);
  }

  beforeEach(() => {
    resetFixture();
  });

  afterEach(() => {
    fixture.destroy();
  });

  describe('button group', () => {
    it('is enabled by default', () => {
      const buttonGroup = fixture.debugElement.query(By.css('#button-group'))
        .nativeElement;
      expect(buttonGroup.getAttribute('ng-reflect-disabled')).toEqual('false');
    });

    it('is disabled when an observation is selected', fakeAsync(() => {
      mockObservationId$.next('oid1');
      tick();
      // wait for async pipe to reflect
      fixture.detectChanges();

      const buttonGroup = fixture.debugElement.query(By.css('#button-group'))
        .nativeElement;
      expect(buttonGroup.getAttribute('ng-reflect-disabled')).toEqual('true');
    }));

    it('is disabled when drawing tools service disables it', fakeAsync(() => {
      mockDisabled$.next(true);
      tick();
      // wait for async pipe to reflect
      fixture.detectChanges();

      const buttonGroup = fixture.debugElement.query(By.css('#button-group'))
        .nativeElement;
      expect(buttonGroup.getAttribute('ng-reflect-disabled')).toEqual('true');
    }));
  });

  describe('add point button', () => {
    it('displays add point button by default', () => {
      const addPointButton = fixture.debugElement.query(
        By.css('#add-point-button')
      ).nativeElement;
      expect(addPointButton).not.toBeNull();
    });

    it('does not display add point button when user cannot add point to any job', () => {
      authServiceSpy.canUserAddPointsToJob.and.returnValue(false);
      resetFixture();

      const addPointButton = fixture.debugElement.query(
        By.css('#add-point-button')
      );
      expect(addPointButton).toBeNull();
    });

    it('sets edit mode to "add point" when add point button clicked', () => {
      const addPointButton = fixture.debugElement.query(
        By.css('#add-point-button')
      ).nativeElement as Element;
      (addPointButton.querySelector('button') as HTMLElement).click();

      expect(drawingToolsServiceSpy.setEditMode).toHaveBeenCalledWith(
        EditMode.AddPoint
      );
    });

    it('sets edit mode back to "none" when add point button clicked twice', () => {
      const addPointButton = fixture.debugElement.query(
        By.css('#add-point-button')
      ).nativeElement as Element;
      (addPointButton.querySelector('button') as HTMLElement).click();
      (addPointButton.querySelector('button') as HTMLElement).click();

      expect(drawingToolsServiceSpy.setEditMode).toHaveBeenCalledWith(
        EditMode.None
      );
    });

    it('turns icon in button green when edit mode set to "add point"', fakeAsync(() => {
      mockEditMode$.next(EditMode.AddPoint);
      tick();

      const greenColor = '#3d7d40';
      const addPointIcon = fixture.debugElement.query(By.css('#add-point-icon'))
        .nativeElement as Element;
      assertElementSrcColor(addPointIcon, greenColor);
    }));

    it('turns icon in button black when edit mode set to "none"', fakeAsync(() => {
      mockEditMode$.next(EditMode.None);
      tick();

      const blackColor = '#202225';
      const addPointIcon = fixture.debugElement.query(By.css('#add-point-icon'))
        .nativeElement as Element;
      assertElementSrcColor(addPointIcon, blackColor);
    }));
  });

  describe('job selector section', () => {
    it('does not display job selector section by default', () => {
      const jobSelectorSection = fixture.debugElement.query(
        By.css('#job-selector-section')
      );
      expect(jobSelectorSection).toBeNull();
    });

    it('displays job selector section when edit mode set to "add point"', fakeAsync(() => {
      mockEditMode$.next(EditMode.AddPoint);
      tick();

      const jobSelectorSection = fixture.debugElement.query(
        By.css('#job-selector-section')
      ).nativeElement;
      expect(jobSelectorSection).not.toBeNull();
      const jobSelectorLabel = fixture.debugElement.query(
        By.css('#job-selector-label')
      ).nativeElement;
      expect(jobSelectorLabel.innerHTML).toEqual('Adding point for');
    }));

    it('selects first job id by default', () => {
      expect(drawingToolsServiceSpy.setSelectedJobId).toHaveBeenCalledWith(
        jobId1
      );
    });

    it('selects job id when job selected', fakeAsync(() => {
      mockEditMode$.next(EditMode.AddPoint);
      tick();

      const jobSelector = fixture.debugElement.query(
        By.css('#job-selector')
      ).nativeElement;
      jobSelector.click();
      // wait for dropdown menu to reflect
      fixture.detectChanges();
      const job2Item = fixture.debugElement.query(
        By.css(`#job-selector-item-${jobId2}`)
      ).nativeElement;
      job2Item.click();
      flush();

      expect(drawingToolsServiceSpy.setSelectedJobId).toHaveBeenCalledWith(
        jobId2
      );
    }));

    it('sets edit mode to "none" when cancel button clicked', fakeAsync(() => {
      mockEditMode$.next(EditMode.AddPoint);
      tick();

      const cancelButton = fixture.debugElement.query(By.css('#cancel-button'))
        .nativeElement;
      cancelButton.click();

      expect(drawingToolsServiceSpy.setEditMode).toHaveBeenCalledWith(
        EditMode.None
      );
    }));

    it('displays icons with job color and name in job selector items', fakeAsync(() => {
      mockEditMode$.next(EditMode.AddPoint);
      tick();

      const jobSelector = fixture.debugElement.query(
        By.css('#job-selector')
      ).nativeElement;
      jobSelector.click();
      // wait for dropdown menu to reflect
      fixture.detectChanges();
      flush();

      const job1Item = fixture.debugElement.query(
        By.css(`#job-selector-item-${jobId1}`)
      ).nativeElement as Element;
      const job2Item = fixture.debugElement.query(
        By.css(`#job-selector-item-${jobId2}`)
      ).nativeElement as Element;
      assertElementSrcColor(
        job1Item.querySelector('img') as Element,
        jobColor1
      );
      assertElementSrcColor(
        job2Item.querySelector('img') as Element,
        jobColor2
      );
      expect(job1Item.innerHTML).toContain(jobName1);
      expect(job2Item.innerHTML).toContain(jobName2);
    }));
  });
});
