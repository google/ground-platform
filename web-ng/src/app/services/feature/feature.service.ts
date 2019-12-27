import { DataStoreService } from './../data-store/data-store.service';
import { switchMap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ProjectService } from './../project/project.service';
import { Injectable, OnInit } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class FeatureService implements OnInit {
  // TODO: Abstract DocumentChangeAction.
  private feature$: Observable<Feature[]>;

  constructor(
    private dataStore: DataStoreService,
    private projectService: ProjectService
  ) {
    this.feature$ = projectService
      .getActiveProject$()
      .pipe(switchMap(project => dataStore.streamFeatures$(project)));
  }

  // TODO: Implement feature$ getter.
}
