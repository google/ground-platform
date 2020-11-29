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

import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AngularFireModule } from '@angular/fire';
import { AngularFireAuthModule } from '@angular/fire/auth';
import { AngularFirestoreModule, USE_EMULATOR } from '@angular/fire/firestore';
import { AppRoutingModule } from './routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MainPageContainerModule } from './components/main-page-container/main-page-container.module';
import { environment } from '../environments/environment';
import { ProjectHeaderModule } from './components/project-header/project-header.module';
import { UserProfilePopupModule } from './components/user-profile-popup/user-profile-popup.module';
import { LayerDialogModule } from './components/layer-dialog/layer-dialog.module';
import { ShareDialogModule } from './components/share-dialog/share-dialog.module';
import { HttpClientModule } from '@angular/common/http';

@NgModule({
  declarations: [AppComponent],
  providers: [
    { provide: USE_EMULATOR, useValue: environment.firebaseConfig.emulator },
  ],
  imports: [
    AngularFireModule.initializeApp(environment.firebaseConfig),
    AngularFireAuthModule,
    AngularFirestoreModule,
    BrowserAnimationsModule,
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    MainPageContainerModule,
    ProjectHeaderModule,
    UserProfilePopupModule,
    LayerDialogModule,
    ShareDialogModule,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
