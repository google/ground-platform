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

### Set up service account and keys

In order to synchronize data with Google Sheets, Ground uses a special Google Cloud Service Account. This prevents project creators from needing to authorize Ground to read and write files in their Google Drive files each time a spreadsheet is linked. Instead, project creators can simply grant the service account Edit access to the linked spreadsheet.

To create a new service account:

https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating_a_service_account

Select the "Console" tap for instructions on creating the account through the Google Cloud Console UI.

Make a note of the email address of the service account. This is the account users will need to allow access to their spreadsheets.

Next, create a new private key:

https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys

When prompted for key type, select "JSON". Download and save the resulting key file in <code>functions/service-account-secret.json</code>. As the name implies, this file should be kept secret, and should not be checked into source control. As with other keys, this filename has already been added to this project's .gitignore file.

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

You can the run the local functions emulator with:

```
firebase functions:shell
```

## Deploy to Firebase hosting

To deploy the web app, Cloud Functions, and Firestore config to Firebase:

```
$ firebase deploy
```

