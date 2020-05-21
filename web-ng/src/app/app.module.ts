/**
 * Copyright 2019 Google LLC
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

import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';

import {AngularFireModule} from '@angular/fire';
import {AngularFireAuthModule} from '@angular/fire/auth';
import {AngularFirestoreModule} from '@angular/fire/firestore';
import {AppRoutingModule} from './routing.module';
import {AppComponent} from './app.component';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {MainPageModule} from './components/main-page/main-page.module';
import {ColorPickerModule} from './components/color-picker/color-picker.module';
import {environment} from '../environments/environment';
import {EditStyleButtonModule} from './components/edit-style-button/edit-style-button.module';
import {InlineEditTitleModule} from './components/inline-edit-title/inline-edit-title.module';
import {ProjectHeaderModule} from './components/project-header/project-header.module';
import {UserProfilePopupModule} from './components/user-profile-popup/user-profile-popup.module';
import {LayerDialogModule} from './components/layer-dialog/layer-dialog.module';

@NgModule({
  declarations: [AppComponent],
  imports: [
    AngularFireModule.initializeApp(environment.firebaseConfig),
    AngularFireAuthModule,
    AngularFirestoreModule,
    BrowserAnimationsModule,
    BrowserModule,
    AppRoutingModule,
    MainPageModule,
    ColorPickerModule,
    EditStyleButtonModule,
    InlineEditTitleModule,
    ProjectHeaderModule,
    UserProfilePopupModule,
    LayerDialogModule,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
