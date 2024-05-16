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

import { initializeApp, provideFirebaseApp } from '@angular/fire/app';
import {HttpClientModule} from '@angular/common/http';
import {NgModule} from '@angular/core';
import {AngularFireModule, FIREBASE_OPTIONS} from '@angular/fire/compat';
import { provideAuth, getAuth } from '@angular/fire/auth'; 
import { AngularFireAuthModule } from '@angular/fire/compat/auth';
import {USE_EMULATOR as USE_DATABASE_EMULATOR} from '@angular/fire/compat/database';
import {
  AngularFirestoreModule,
  SETTINGS as FIRESTORE_SETTINGS,
  USE_EMULATOR as USE_FIRESTORE_EMULATOR,
} from '@angular/fire/compat/firestore';
import {
  AngularFireFunctionsModule,
  USE_EMULATOR as USE_FUNCTIONS_EMULATOR,
} from '@angular/fire/compat/functions';
import { provideStorage, getStorage, StorageModule } from '@angular/fire/storage';
import {BrowserModule} from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {initializeApp} from 'firebase/app';
import {GoogleAuthProvider} from 'firebase/auth';
import {FirebaseUIModule, firebaseui} from 'firebaseui-angular';

import {AppComponent} from 'app/app.component';
import {MainPageContainerModule} from 'app/pages/main-page-container/main-page-container.module';
import {AppRoutingModule} from 'app/routing.module';
import {environment} from 'environments/environment';
import { AuthService } from './services/auth/auth.service';
import { AuthGuard } from './services/auth/auth.guard';

const firebaseUiAuthConfig: firebaseui.auth.Config = {
  // Popup is required to prevent some browsers and Chrome incognito for getting
  // blocked due to unsupported 3rd party cookies.
  signInFlow: 'popup',
  // For now we only use Google for auth.
  signInOptions: [GoogleAuthProvider.PROVIDER_ID],
  // Required to enable one-tap sign-up credential helper.
  credentialHelper: firebaseui.auth.CredentialHelper.GOOGLE_YOLO,
};

initializeApp(environment.firebase);

@NgModule({
  declarations: [AppComponent],
  providers: [
    {
      provide: FIRESTORE_SETTINGS,
      useValue: {ignoreUndefinedProperties: true},
    },
    { provide: FIREBASE_OPTIONS, useValue: environment.firebase },
    AuthService,
    AuthGuard,
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
    provideFirebaseApp(() => initializeApp(environment.firebase)),
    AngularFireModule,
    provideAuth(() => getAuth()), 
    AngularFireAuthModule,
    provideStorage(() => getStorage()),
    StorageModule,
    AngularFirestoreModule,
    AngularFireFunctionsModule,
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
