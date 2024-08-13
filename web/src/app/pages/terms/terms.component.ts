/**
 * Copyright 2024 The Ground Authors.
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

import {Location} from '@angular/common';
import {Component, OnInit} from '@angular/core';

import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {NavigationService} from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-terms-page',
  templateUrl: './terms.component.html',
  styleUrls: ['./terms.component.scss'],
})
export class TermsComponent implements OnInit {
  hasAcceptedTos: boolean;
  isTermsOfServiceChecked = false;
  termsOfServiceText = '';

  constructor(
    private authService: AuthService,
    private dataStore: DataStoreService,
    private navigationService: NavigationService,
    private _location: Location
  ) {
    this.hasAcceptedTos = this.authService.getHasAcceptedTos();
  }

  async ngOnInit() {
    this.termsOfServiceText = await this.dataStore.getTermsOfService();
  }

  onContinueButtonClick() {
    this.authService.approveTos();
    this.navigationService.navigateToSurveyList();
  }

  onBackButtonClick() {
    this._location.back();
  }
}
