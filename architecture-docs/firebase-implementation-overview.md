<!--
  Copyright 2021 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Firebase implementation overview

The default implementation of Ground relies on several services provided by the Firebase platform, including authentication, analytics, and data storage and retrieval. 

Currently, Ground uses the following Firebase components:

- [**Cloud Firestore**](https://firebase.google.com/docs/firestore) is used for storing Ground data, which is organized into collections and documents. For more information on how Ground data is is represented in Cloud Firestore, see [Cloud Firestore Representation](https://github.com/google/ground-platform/wiki/Cloud-Firestore-Representation).
- [**Cloud Functions for Firebase**](https://firebase.google.com/docs/functions) are used for two features: 
    - Data import and export using HTTP functions accessible via a browser.
    - Database triggers used to synchronize data to Google Sheets (feature still in development, such as for profile creation and updates.
- [**Firebase Authentication**](https://firebase.google.com/docs/auth) is used by both the Ground web app and Ground Android app for user authentication.
- [**Firebase Cloud Storage**](https://firebase.google.com/docs/storage) is used to host images uploaded by users. 
- [**Firebase Crashlytics**](https://firebase.google.com/docs/crashlytics) is used by the Ground Android app for crash reporting.
