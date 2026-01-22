import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { EditSurveyComponent } from 'app/components/edit-survey/edit-survey.component';
import { Survey } from 'app/models/survey.model';
import { ShareSurveyComponent } from './share-survey.component';

describe('ShareSurveyComponent', () => {
  let component: ShareSurveyComponent;
  let fixture: ComponentFixture<ShareSurveyComponent>;

  const mockEditSurveyComponent = {
    survey: signal<Survey | undefined>(undefined),
    updateAcl: jasmine.createSpy('updateAcl'),
    updateGeneralAccess: jasmine.createSpy('updateGeneralAccess'),
    updateDataVisibility: jasmine.createSpy('updateDataVisibility'),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatIconModule, MatDialogModule, MatCardModule],
      declarations: [ShareSurveyComponent],
      providers: [
        { provide: EditSurveyComponent, useValue: mockEditSurveyComponent },
        {
          provide: MatDialog,
          useValue: { open: jasmine.createSpy('open') },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(ShareSurveyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
