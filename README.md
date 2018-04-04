# Ground Cloud Components

This project consists of three cloud-based components, each in their own respective subdirectories:

* web: The Ground web UI
* functions: Firebase Cloud Functions used to sync with Google Sheets and other data sources
* firestore: Configuration for Ground's main Firestore instance

## Initial setup

### Add Firebase credentials

Create a file in the project root called <code>firebase-init.html</code> containing the second <code>&lt;script&gt;</code> block shown in: Firebase Console > Overview > Add another app > Add Firebase to web app. For example:

```javascript
<script>
  // Initialize Firebase
  var config = {
    apiKey: "ABcdEfGhIjKLmnOpQrS_tuVw",
    authDomain: "your-project.firebaseapp.com",
    databaseURL: "https://your-project.firebaseio.com",
    projectId: "your-project",
    storageBucket: "your-project.appspot.com",
    messagingSenderId: "1234567890"
  };
  firebase.initializeApp(config);
</script>
```

**IMPORTANT**: Be sure to omit the first line that includes <code>firebase.js</code>:

```javascript
<script src="https://www.gstatic.com/firebasejs/<FIREBASE_VERSION>/firebase.js"></script>
```

### Add Google Maps API Key

Generate a new API key at:

https://developers.google.com/maps/documentation/android-api/signup.

Create <code>google-maps-api-key.html</code> in the project root, substituting <code>YOUR_API_KEY</code> for your Google Maps API Key:

```html
<iron-meta key="googleMapsApiKey" value="YOUR_API_KEY"></iron-meta>
```

## Building, running, deploying

### Install the CLI tools

First, make sure you have [Polymer CLI](https://www.npmjs.com/package/polymer-cli) and [Firebase CLI](https://firebase.google.com/docs/cli/).

### Running Ground web UI locally

To run the web app locally without doing a full build:

```
$ cd web && polymer serve & cd ..
```

### Build and deploy to Firebase hosting

To build the web app, from the project root (gnd-cloud):

```
$ cd web && polymer install && polymer build && cd ..
```

Download dependencies required by Ground Cloud Functions:

```
$ cd functions && npm install && cd ..
```

Log in using Firebase CLI tools:

```
$ firebase login
```

Then, deploy the web app, Cloud Functions, and Firestore config using:

```
$ firebase deploy --project <projectId>
```

Be sure to replace <code>&lt;projectId&gt;</code> with the ID shown in the Firebase Console under "Project settings".

