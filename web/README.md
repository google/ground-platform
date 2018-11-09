
# Ground Web Console

## Web developer setup

All instructions below assume "web/" is the current working directory.

## Install NVM

If you haven't already done so, install Node Version Manager (NVM) following the instructions found at:

  https://github.com/creationix/nvm#installation

## Install Node.js

The first time you can install the appropriate Node.js version with:

```
$ nvm install 10.3.0
```

You can set this as the default version with:

```
$ nvm alias default 10.3.0
```

### Add Firebase credentials

* Visit to https://firebase.corp.google.com/ and create and/or select your development instance.
* Click "Project Overview".
* Click the "+ Add app" chip shown under the main heading, then click the web icon ("&lt;/&gt;").
* Copy the second key/value pairs that appear there into a new file,
  `src/.firebase-config.js `, surrounding it with an export block as shown 
  here:

```javascript
export default {
  apiKey: "somethingsomething",
  authDomain: "your-project.firebaseapp.com",
  databaseURL: "https://your-project.firebaseio.com",
  projectId: "your-project",
  storageBucket: "your-project.appspot.com",
  messagingSenderId: "1234567890"
};
```

### Add Google Maps API Key

If you don't already have one, generate a new API key at:

https://developers.google.com/maps/documentation/android-sdk/signup#quick-guide

Alternatively, you can access an existing project's key via [Google Cloud
Platform](https://console.cloud.google.com/) under APIs &amp; Services &;gt;
Credentials.

Write the key to `src/.google-maps-config.js` as follows, substituting `
YOUR_API_KEY` with your own:

```html
export default {
  apiKey: "YOUR_API_KEY"
}
```

## Developer workflow

### Select Node.js version

If you've changed Node.js versions (e.g., to work on the Ground Firebase Cloud 
Functions), you can switch back manually with:

```
$ nvm use 10.3.0
```


### Building

First, from the `web` directory, download all required Node.js deps using:

```
$ npm install
```

To run the web app locally from source without doing a full build:

```
$ npm start
```


You should then see the demo project running locally, for example, at:

  `http://127.0.0.1:3000/p/project_id`

Where `project_id` is the id of a Ground project defined in the Cloud Firestore database, or `:new` for a new, unsaved Ground project.

## Deployment

### Build

To build the web app for deployment:

```
$ npm run build
```

### Deploy

The first time deploying, install the Firebase command-line tools:

```
$ npm install -g firebase-tools
```

And authenticate to obtain a local token using:

```
$ firebase login
```

Once authenticated, select your Firebase project using:

```
$ firebase use --add gnddemo1
```

Finally, from the root of gnd-cloud, deploy the built artifact to Firebase web
hosting:

```
$ pushd .. && firebase deploy --only hosting && popd
```

