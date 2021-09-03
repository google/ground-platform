/**
 * Copyright 2020 Google LLC
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

import { Component, OnInit } from '@angular/core';
import { NavigationService } from '../../services/navigation/navigation.service';

@Component({
  selector: 'gnd-header-layout',
  templateUrl: './header-layout.component.html',
  styleUrls: ['./header-layout.component.scss'],
})
export class HeaderLayoutComponent implements OnInit {
  constructor(public navigationService: NavigationService) {}

  ngOnInit(): void {}

  onProjectsButtonClick(): void {
    this.navigationService.navigateToProjectList();
  }
}
