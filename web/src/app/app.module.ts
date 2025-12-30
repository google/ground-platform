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

import {
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { NgModule } from '@angular/core';
import { provideFirebaseApp } from '@angular/fire/app';
import { provideAuth, getAuth } from '@angular/fire/auth';
import {
  provideFirestore,
  getFirestore,
  connectFirestoreEmulator,
} from '@angular/fire/firestore';
import {
  provideFunctions,
  getFunctions,
  connectFunctionsEmulator,
} from '@angular/fire/functions';
import {
  provideRemoteConfig,
  getRemoteConfig,
} from '@angular/fire/remote-config';
import {
  provideStorage,
  getStorage,
  connectStorageEmulator,
} from '@angular/fire/storage';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { initializeApp } from 'firebase/app';
import { GoogleAuthProvider } from 'firebase/auth';
import { FirebaseUIModule, firebaseui } from 'firebaseui-angular';

import { AppComponent } from 'app/app.component';
import { MainPageContainerModule } from 'app/components/main-page-container/main-page-container.module';
import { AppRoutingModule } from 'app/routing.module';
import { environment } from 'environments/environment';
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
  bootstrap: [AppComponent],
  imports: [
    BrowserAnimationsModule,
    BrowserModule,
    AppRoutingModule,
    FirebaseUIModule.forRoot(firebaseUiAuthConfig),
    MainPageContainerModule,
  ],
  providers: [
    provideFirebaseApp(() => initializeApp(environment.firebase as any)),
    provideAuth(() => {
      const auth = getAuth();
      // TODO: https://github.com/google/ground-platform/issues/979
      //   Set up auth emulator and enable rules.
      // if (environment.useEmulators) {
      //   connectAuthEmulator(auth, 'http://localhost:9099');
      // }
      return auth;
    }),
    provideFirestore(() => {
      const firestore = getFirestore();
      if (environment.useEmulators) {
        connectFirestoreEmulator(firestore, 'localhost', 8080);
      }
      return firestore;
    }),
    provideFunctions(() => {
      const functions = getFunctions();
      if (environment.useEmulators) {
        connectFunctionsEmulator(functions, 'localhost', 5001);
      }
      return functions;
    }),
    provideRemoteConfig(() => getRemoteConfig()),
    provideStorage(() => {
      const storage = getStorage();
      if (environment.useEmulators) {
        connectStorageEmulator(storage, 'localhost', 9199);
      }
      return storage;
    }),
    provideHttpClient(withInterceptorsFromDi()),
  ],
})
export class AppModule {}
