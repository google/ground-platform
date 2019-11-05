import { TestBed, async } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { GroundApp } from './ground_app';

describe('AppComponent', () => {
  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        RouterTestingModule
      ],
      declarations: [
        GroundApp
      ],
    }).compileComponents();
  }));

  it('should create the app', () => {
    const fixture = TestBed.createComponent(GroundApp);
    const app = fixture.debugElement.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have as title 'Ground'`, () => {
    const fixture = TestBed.createComponent(GroundApp);
    const app = fixture.debugElement.componentInstance;
    expect(app.title).toEqual('Ground');
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(GroundApp);
    fixture.detectChanges();
    const compiled = fixture.debugElement.nativeElement;
    expect(compiled.querySelector('span').textContent).toContain('Ground app is running!');
  });
});
