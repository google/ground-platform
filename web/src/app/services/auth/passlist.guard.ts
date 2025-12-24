/**
 * Copyright 2025 The Ground Authors.
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

import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService } from 'app/services/auth/auth.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

/**
 * Functional Guard to check passlist status and redirect if needed.
 */
export const passlistGuard: CanActivateFn = async (): Promise<
  boolean | UrlTree
> => {
  const authService = inject(AuthService);
  const navigationService = inject(NavigationService);
  const router = inject(Router);

  const isPasslisted = await authService.isPasslisted();

  if (isPasslisted) {
    return true;
  }

  const url = await navigationService.getAccessDeniedLink();

  return router.parseUrl(url || '/');
};
