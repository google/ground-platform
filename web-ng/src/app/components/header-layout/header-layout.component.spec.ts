import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { HeaderLayoutComponent } from './header-layout.component';

describe('HeaderLayoutComponent', () => {
  let component: HeaderLayoutComponent;
  let fixture: ComponentFixture<HeaderLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [HeaderLayoutComponent],
      providers: [{ provide: Router, useValue: {} }],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HeaderLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
