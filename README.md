# Ground Cloud Components

This project consists of three cloud-based components, each in their own respective subdirectories:

* web: The Ground web UI
* functions: Firebase Cloud Functions used to sync with Google Sheets and other data sources
* firestore: Configuration for Ground's main Firestore instance

Throughout this guide, be sure to replace &lt;projectId&gt; with your Firebase project ID exactly as it appears in the Firebase Console under "Project settings".

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

### Add OAuth 2.0 client ID

Add a new API key at:

https://pantheon.corp.google.com/apis/credentials/oauthclient

Be sure the correct project is selected from the drop-down, then select Application type "Web application", and add the following URL to "Authorized redirect URLs":

https://&lt;projectId&gt;.firebaseapp.com/oauthcallback

Once the key is created, click "Download JSON", saving the file to <code>functions/client-secret.json</code> (with a dash, not an underscore).

### Install and configure the CLI tools

First, make sure you have [Polymer CLI](https://www.npmjs.com/package/polymer-cli) and [Firebase CLI](https://firebase.google.com/docs/cli/).

Log in using Firebase CLI tools:

```
$ firebase login
```

Select your Firebase project with:

```
$ firebase use --add &lt;projectId&gt;
```

## Development

### Building

To build the web app, from the project root (gnd-cloud):

```
$ cd web && polymer install && polymer build && cd ..
```

Download dependencies required by Ground Cloud Functions:

```
$ cd functions && npm install && cd ..
```

### Running web app locally

To run the web app locally without doing a full build:

```
$ cd web && polymer serve & cd ..
```

### Running Cloud Functions locally

Before running the first time, fetch the database config with:
```
$ firebase functions:config:get > .runtimeconfig.json
```

Then r

## Deploy to Firebase hosting
You can then deploy the web app, Cloud Functions, and Firestore config to Firebase hosting using:

```
$ firebase deploy
```

