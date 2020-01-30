// TODO: Copyright headers!

import { Observation } from './../../shared/models/observation/observation.model';
import { ObservationService } from './../../services/observation/observation.service';
import { FeatureService } from './../../services/feature/feature.service';
import { switchMap } from 'rxjs/operators';
import { ProjectService } from './../../services/project/project.service';
import { List } from 'immutable';
import { Observable } from 'rxjs';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-feature-panel',
  templateUrl: './feature-panel.component.html',
  styleUrls: ['./feature-panel.component.css'],
})
export class FeaturePanelComponent implements OnInit {
  private observations$: Observable<List<Observation>>;

  constructor(
    private projectService: ProjectService,
    private featureService: FeatureService,
    private observationService: ObservationService
  ) {
    this.observations$ = projectService
      .getActiveProject$()
      .pipe(
        switchMap(project =>
          featureService
            .getActiveFeature$()
            .pipe(
              switchMap(feature =>
                observationService.observations$(project, feature)
              )
            )
        )
      );
  }

  ngOnInit() {}
}
