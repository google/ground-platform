/**
 * Copyright 2019 The Ground Authors.
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

import {DOCUMENT} from '@angular/common';
import {Component, Inject} from '@angular/core';

import {environment} from 'environments/environment';
import {Env} from 'environments/environment-enums';

/**
 * Top-level component. Delegates routing of sub-components to paths defined
 * in routing.module.ts.
 */
@Component({
  selector: 'ground-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.css'],
})
export class AppComponent {
  public constructor(@Inject(DOCUMENT) private doc: Document) {
    if (environment.env !== Env.Test) {
      this.initGoogleMap();
    }
  }

  private initGoogleMap(): void {
    const script = this.doc.createElement('script');
    script.type = 'text/javascript';
    script.src = `https://maps.googleapis.com/maps/api/js?key=${environment.googleMapsApiKey}&libraries=marker`;
    const head = this.doc.getElementsByTagName('head')[0];
    head.appendChild(script);
  }
}
