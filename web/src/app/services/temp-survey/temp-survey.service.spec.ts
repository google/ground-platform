import {TestBed} from '@angular/core/testing';

import {TempSurveyService} from './temp-survey.service';

describe('TempSurveyService', () => {
  let service: TempSurveyService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TempSurveyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
