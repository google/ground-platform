import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CurrentUserWidgetComponent } from './current-user-widget.component';

describe('CurrentUserWidgetComponent', () => {
  let component: CurrentUserWidgetComponent;
  let fixture: ComponentFixture<CurrentUserWidgetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ CurrentUserWidgetComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CurrentUserWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
