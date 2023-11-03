import {TestBed} from '@angular/core/testing';

import {EditSurveyService} from './temp-survey.service';

describe('EditSurveyService', () => {
  let service: EditSurveyService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EditSurveyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
