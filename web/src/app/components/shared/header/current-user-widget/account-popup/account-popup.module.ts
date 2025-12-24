/**
 * Copyright 2020 The Ground Authors.
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

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';

import { AccountPopupComponent } from 'app/components/shared/header/current-user-widget/account-popup/account-popup.component';
import { UserAvatarModule } from 'app/components/shared/user-avatar/user-avatar.module';

@NgModule({
  declarations: [AccountPopupComponent],
  imports: [CommonModule, MatButtonModule, MatDialogModule, UserAvatarModule],
})
export class AccountPopupModule {}
