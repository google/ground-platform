/**
 * Copyright 2023 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {Subscription, filter, startWith} from 'rxjs';

import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {SubmissionService} from 'app/services/submission/submission.service';

@Component({
  selector: 'ground-secondary-side-panel',
  templateUrl: './secondary-side-panel.component.html',
  styleUrls: ['./secondary-side-panel.component.css'],
})
export class SecondarySidePanelComponent implements OnInit {
  subscription: Subscription = new Subscription();
  locationOfInterestId: string | null = null;
  submissionId: string | null = null;

  constructor(
    private loiService: LocationOfInterestService,
    private route: ActivatedRoute,
    private router: Router,
    private submissionService: SubmissionService
  ) {}

  ngOnInit() {
    this.subscription.add(
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd),
          startWith(this.router)
        )
        .subscribe(_ => {
          this.locationOfInterestId =
            this.route.snapshot.paramMap.get('siteId');
          this.submissionId = this.route.snapshot.paramMap.get('submissionId');
          this.loiService.selectLocationOfInterest(this.locationOfInterestId!);
          this.submissionService.selectSubmission(this.submissionId!);
          console.log('LOI ID (snapshot):', this.locationOfInterestId);
          console.log('SUBMISSION ID (snapshot):', this.submissionId);
        })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
