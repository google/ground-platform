
# Ground Web Console

## Web developer setup

Followed pre-requisite steps in [../README.md](..) before proceeding.

All instructions below assume "web/" is the current working directory.

## Install Node.js

The first time you can install the appropriate Node.js version with:

```
$ nvm install 8.0
```

You can set this as the default version with:

```
$ nvm alias default 8.0
```

Next, install [Polymer CLI](https://www.polymer-project.org/2.0/docs/tools/polymer-cli) and [Firebase CLI](https://firebase.google.com/docs/cli/) globally to your system (`-g`) using Node Package Manager (NPM):

```
$ npm install -g polymer-cli firebase-tools
```

Replacing `gnddemo1` with your Firebase project name.

### Add Firebase credentials

* Visit to https://firebase.corp.google.com/ and create and/or select your development instance.
* Click "Project Overview".
* Click the "+ Add app" chip shown under the main heading, then click the web icon ("&lt;/&gt;").
* Copy the second <code>&lt;script&gt;</code> block shown into <code>firebase-init.html</code>, for example:

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

**IMPORTANT**: Be sure to **omit** the first line that includes <code>firebase.js</code>:

```javascript
<script src="https://www.gstatic.com/firebasejs/<FIREBASE_VERSION>/firebase.js"></script>
```

### Add Google Maps API Key

Generate a new API key at:

https://developers.google.com/maps/documentation/android-api/signup.

Write the following to <code>google-maps-api-key.html</code>, substituting <code>YOUR_API_KEY</code> with your Google Maps API Key:

```html
<iron-meta key="googleMapsApiKey" value="YOUR_API_KEY"></iron-meta>
```

## Developer workflow

### Select Node.js version

You can switch versions manually when opening a new shell with:

```
$ nvm use 8.0
```

### Building

First, download all bower deps with:

```
$ polymer install
```

To run the web app locally from source without doing a full build:

```
$ polymer serve
```

You should then see the demo project running locally, for example, at:

  http://127.0.0.1:8081/projects/R06MucQJSWvERdE7SiL1

Where `R06MucQJSWvERdE7SiL1` is the id of a Ground project defined in the Cloud Firestore database.

## Deployment


### Build and test locally

To create a build of the web app, run:

```
$ polymer build
```

You can serve the built version with:

```
$ polymer serve build/default
```

### Deploy

The first time deploying, log into Firebase with:

```
$ firebase login
```

Once authenticated, select your Firebase project with:

```
$ firebase use --add gnddemo1
```

Finally, to deploy the built artifact to Firebase web hosting:

```
$ firebase deploy --only hosting
```

