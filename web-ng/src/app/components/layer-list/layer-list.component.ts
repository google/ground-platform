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

import { Component, Input, OnChanges } from '@angular/core';
import { Layer } from '../../shared/models/layer.model';
import { List } from 'immutable';
import { NavigationService } from '../../services/router/router.service';
import { Project } from './../../shared/models/project.model';

@Component({
  selector: 'ground-layer-list',
  templateUrl: './layer-list.component.html',
  styleUrls: ['./layer-list.component.scss'],
})
export class LayerListComponent implements OnChanges {
  @Input() project!: Project;
  layers!: List<Layer>;
  lang: string;

  constructor(private navigationService: NavigationService) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
  }

  ngOnChanges() {
    this.layers = List(this.project.layers.valueSeq().toArray()).sortBy(
      l => l.index
    );
  }

  onAddLayer() {
    this.navigationService.setLayerId(NavigationService.LAYER_ID_NEW);
  }
}
