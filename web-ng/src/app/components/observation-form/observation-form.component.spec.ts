import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ObservationFormComponent } from './observation-form.component';

describe('ObservationFormComponent', () => {
  let component: ObservationFormComponent;
  let fixture: ComponentFixture<ObservationFormComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ObservationFormComponent],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ObservationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
