import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';

import {ShareSurveyComponent} from './share-survey.component';

describe('ShareSurveyComponent', () => {
  let component: ShareSurveyComponent;
  let fixture: ComponentFixture<ShareSurveyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatIconModule, MatDialogModule],
      declarations: [ShareSurveyComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ShareSurveyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
