# Ground Cloud Platform

This project consists of three components:

* The Ground Web Console
* Cloud Functions used by Ground for syncing with Google Sheets and other data sources
* Configuration for Ground's Firestore instance

## Add Firebase credentials

Create a file in the project root called <code>firebase-init.html</code> containing the second <code>&lt;script&gt;</code> block shown in: Firebase Console > Overview > Add another app > Add Firebase to web app. For example:

```javascript
<script>
  // Initialize Firebase
  var config = {
    apiKey: "ALzaSaBdlVtLRvfZ1NtYlrR-X1odIhLdEd_D24d",
    authDomain: "myproject.firebaseapp.com",
    databaseURL: "https://myproject.firebaseio.com",
    projectId: "myproject",
    storageBucket: "myproject.appspot.com",
    messagingSenderId: "673489732974"
  };
  firebase.initializeApp(config);
</script>
```

**IMPORTANT**: Be sure to omit the first line that includes <code>firebase.js</code>:

```javascript
<script src="https://www.gstatic.com/firebasejs/<FIREBASE_VERSION>/firebase.js"></script>
```

## Add Google Maps API Key

Generate a new API key at:

https://developers.google.com/maps/documentation/android-api/signup.

Create <code>google-maps-api-key.html</code> in the project root, substituting <code>YOUR_API_KEY</code> for your Google Maps API Key:

```html
<iron-meta key="googleMapsApiKey" value="YOUR_API_KEY"></iron-meta>
```

## Install the CLI tools

First, make sure you have [Polymer CLI](https://www.npmjs.com/package/polymer-cli) and [Firebase CLI](https://firebase.google.com/docs/cli/).

## Running Ground Web locally

To run the web app locally without building:

```
$ cd web && polymer serve & cd ..
```

## Build and deploy to Firebase hosting

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

Then to deploy the web app, Cloud Functions, and Firestore config:

```
$ firebase deploy --project <projectId>
```

Replacing <code>projectId</code> with the ID shown in the Firebase Console under "Project settings".

