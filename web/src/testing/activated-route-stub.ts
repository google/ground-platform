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

import { ParamMap, Params, convertToParamMap } from '@angular/router';
import { ReplaySubject } from 'rxjs';

/**
 * An ActivateRoute test double with `paramMap` and `fragment` observables.
 * Use the `setParamMap()` method to add the next `paramMap` value, and
 * `setFragment` to change the URL `fragment`.
 */
export class ActivatedRouteStub {
  // Use a ReplaySubjects to share previous values with subscribers
  // and pump new values into observables.
  private paramMap$ = new ReplaySubject<ParamMap>();
  private fragment$ = new ReplaySubject<string | undefined>();

  /** The mock paramMap observable field. */
  readonly paramMap = this.paramMap$.asObservable();

  /** The mock fragment observable field. */
  readonly fragment = this.fragment$.asObservable();

  constructor(initialParams?: Params, initialFragment?: string) {
    this.setParamMap(initialParams);
    this.setFragment(initialFragment);
  }

  /** Set the paramMap observables's next value */
  setParamMap(params?: Params) {
    this.paramMap$.next(convertToParamMap(params || {}));
  }

  /** Set the fragment observables's next value */
  setFragment(fragment?: string) {
    this.fragment$.next(fragment);
  }
}
