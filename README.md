# Ground Web

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

**IMPORTANT**: Be sure ***not*** to copy the first script tag that includes <code>firebase.js</code>. It looks like this:

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

## Install the Polymer-CLI

First, make sure you have the [Polymer CLI](https://www.npmjs.com/package/polymer-cli) installed. Then run `polymer serve` to serve your application locally.

## Viewing Your Application

```
$ polymer serve
```

## Building Your Application

```
$ polymer build
```

This will create builds of your application in the `build/` directory, optimized to be served in production. You can then serve the built versions by giving `polymer serve` a folder to serve from:

```
$ polymer serve build/default
```

## Running Tests

```
$ polymer test
```

Your application is already set up to be tested via [web-component-tester](https://github.com/Polymer/web-component-tester). Run `polymer test` to run your application's test suite locally.

### Deploy to Firebase hosting.

```
firebase deploy --only hosting
```
