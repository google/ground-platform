import { TestBed } from '@angular/core/testing';

import { DataStoreService } from './data-store.service';
import { AngularFirestore } from '@angular/fire/firestore';

describe('DataStoreService', () => {
  let angularFirestoreStub: Partial<AngularFirestore> = {};

  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        { provide: AngularFirestore, useValue: angularFirestoreStub },
      ],
    })
  );

  it('should be created', () => {
    const service: DataStoreService = TestBed.get(DataStoreService);
    expect(service).toBeTruthy();
  });
});
