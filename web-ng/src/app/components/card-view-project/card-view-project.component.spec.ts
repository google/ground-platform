import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CardViewProjectComponent } from './card-view-project.component';

describe('CardViewProjectComponent', () => {
  let component: CardViewProjectComponent;
  let fixture: ComponentFixture<CardViewProjectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ CardViewProjectComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(CardViewProjectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
