import {TestBed} from '@angular/core/testing';

import {HamburgerService} from './hamburger.service';

describe('HamburgerService', () => {
  let service: HamburgerService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(HamburgerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
