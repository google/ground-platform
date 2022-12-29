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
import {
  AngularFirestoreModule,
  USE_EMULATOR as USE_FIRESTORE_EMULATOR,
  SETTINGS as FIRESTORE_SETTINGS,
} from '@angular/fire/compat/firestore';
import { USE_EMULATOR as USE_DATABASE_EMULATOR } from '@angular/fire/compat/database';
import { USE_EMULATOR as USE_FUNCTIONS_EMULATOR } from '@angular/fire/compat/functions';
import { AppRoutingModule } from 'app/routing.module';
import { AppComponent } from 'app/app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MainPageContainerModule } from 'app/pages/main-page-container/main-page-container.module';
import { environment } from 'environments/environment';
import { GoogleAuthProvider } from 'firebase/auth';
import { HttpClientModule } from '@angular/common/http';
import { firebaseui, FirebaseUIModule } from 'firebaseui-angular';
import { AngularFireModule } from '@angular/fire/compat';
import { AngularFireAuthModule } from '@angular/fire/compat/auth';

const firebaseUiAuthConfig: firebaseui.auth.Config = {
  // Popup is required to prevent some browsers and Chrome incognito for getting
  // blocked due to unsupported 3rd party cookies.
  signInFlow: 'popup',
  // For now we only use Google for auth.
  signInOptions: [GoogleAuthProvider.PROVIDER_ID],
  // Required to enable one-tap sign-up credential helper.
  credentialHelper: firebaseui.auth.CredentialHelper.GOOGLE_YOLO,
};

@NgModule({
  declarations: [AppComponent],
  providers: [
    {
      provide: FIRESTORE_SETTINGS,
      useValue: { ignoreUndefinedProperties: true },
    },
    // Emulator ports defined in ../firebase.local.json
    // TODO(#979): Set up auth emulator and enable rules.
    {
      provide: USE_DATABASE_EMULATOR,
      useValue: environment.useEmulators ? ['localhost', 9000] : undefined,
    },
    {
      provide: USE_FIRESTORE_EMULATOR,
      useValue: environment.useEmulators ? ['localhost', 8080] : undefined,
    },
    {
      provide: USE_FUNCTIONS_EMULATOR,
      useValue: environment.useEmulators ? ['localhost', 5001] : undefined,
    },
  ],
  imports: [
    // TODO(#967): Replace compat libs with new AngularFire APIs:
    //   provideFirebaseApp(() => initializeApp(environment.firebase)),
    //   provideFirestore(() => getFirestore()),
    AngularFireModule.initializeApp(environment.firebase),
    AngularFireModule,
    AngularFireAuthModule,
    AngularFirestoreModule,
    BrowserAnimationsModule,
    BrowserModule,
    AppRoutingModule,
    FirebaseUIModule.forRoot(firebaseUiAuthConfig),
    HttpClientModule,
    MainPageContainerModule,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
