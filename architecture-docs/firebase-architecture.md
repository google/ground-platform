The Ground platform uses Firebase for many backend and utility operations, including authentication and storage. 

Currently, Ground uses the following Firebase features:

- [**Cloud Firestore**](https://firebase.google.com/docs/firestore) is used for storing Ground observation data. Data in Firestore is organized into collections and documents. For more information on how Ground data is structured, see [Cloud Firestore Representation](https://github.com/google/ground-platform/wiki/Cloud-Firestore-Representation).
- [**Cloud Function for Firebase**](https://firebase.google.com/docs/functions) are used for two features: 
    - Triggers run automatically and respond to changes in Firestore, such as profile creation and updates.
    - HTTP functions are accessible via a browser and are used by the Ground web app for importing and exporting data. HTTP functions are not used by the Ground Android app. 
- [**Firebase Authentication**](https://firebase.google.com/docs/auth) is used by both the Ground web app and Ground Android app for user authentication.
- [**Firebase Hosting**](https://firebase.google.com/docs/hosting) is used to host the current implementation of the Ground web app.
- [**Firebase Cloud Storage**](https://firebase.google.com/docs/storage) is used to host images uploaded by users. 
- [**Firebase Crashlytics**](https://firebase.google.com/docs/crashlytics) is used by the Ground Android app for crash reporting.