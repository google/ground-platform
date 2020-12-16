import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AngularFireAuth } from '@angular/fire/auth';
import { AngularFirestore } from '@angular/fire/firestore';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { NavigationService } from '../../services/router/router.service';

import { FeatureDetailsComponent } from './feature-details.component';

describe('FeatureDetailsComponent', () => {
  let component: FeatureDetailsComponent;
  let fixture: ComponentFixture<FeatureDetailsComponent>;

  beforeEach(async () => {
    const navigationService = {
      getProjectId$: () => of(''),
      getFeatureId$: () => of(''),
    };
    await TestBed.configureTestingModule({
      declarations: [FeatureDetailsComponent],
      imports: [MatIconModule, MatListModule, MatMenuModule, MatDialogModule],
      providers: [
        { provide: Router, useValue: {} },
        { provide: AngularFirestore, useValue: {} },
        { provide: AngularFireAuth, useValue: {} },
        { provide: NavigationService, useValue: navigationService },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FeatureDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
