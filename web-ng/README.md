[![codecov](https://codecov.io/gh/google/ground-platform/branch/master/graph/badge.svg?token=arOkyNs0m9)](https://codecov.io/gh/google/ground-platform)

# Ground Web Console in Angular

## First-time setup

Before proceeding, follow instructions in [../README.md](../README.md)

### Add Google Maps API key

1. If you don't already have one, generate a new API key by following instructions at https://developers.google.com/maps/documentation/android-sdk/signup#quick-guide.

   > **Note:** Alternatively, you can access an existing project's key via [Google Cloud
   > Platform](https://console.cloud.google.com/) under APIs \> Services \>
   > Credentials.

2. Write the key to `src/environments/.google-maps-config.ts` as follows:

   ```javascript
   export const googleMapsConfig = {
     apiKey: 'YOUR_API_KEY',
   };
   ```

   Where `YOUR_API_KEY` is your Google Maps API key.

### Download Firebase API key

1. Visit https://console.firebase.google.com and click "Add project".

1. Once your project is created, on the welcome page under "Get started by
   adding Firebase to your app", click the Web icon (`</>`).

1. In Step 1, select "Also set up Firebase hosting for this app".

1. Accept defaults for remaining steps to return the main console page.

1. Click "1 app" and then click the gear icon next to your app name.

1. Scroll down to "Firebase SDK snippet" and select "Config".

1. Copy the text that appears in "const firebaseConfig..." into a new file in
   `src/environments/.firebase-config.ts`, prepending the keyword `export` to
   the file contents. The contents of the file should look roughly like this:

   ```javascript
   export const firebaseConfig = {
     apiKey: 'soMeReallYlOngApIkeyWouLdGoHere123',
     authDomain: 'my-app.firebaseapp.com',
     databaseURL: 'https://my-app.firebaseio.com',
     projectId: 'my-app',
     storageBucket: 'my-app.appspot.com',
     messagingSenderId: '12345678',
     appId: '1:12345678:web:abc123etcetc',
   };
   ```

### Add local environment config

Create a file in `environments/.backend-config.json` containing the following:

```json
{
  "offlineBaseMapSources": []
}
```

## Install dependencies

Install required npm dependencies:

```
$ npm install
```

## Development server

Run `npm run start` to host your web app locally, then navigate to
<http://localhost:4200/project/new> to create a new Ground project. The app will
automatically rebuild and reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can
also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the
`dist/` directory. Use the `--prod` flag for a production build.

## Running unit tests

Run `ng test` to execute run tests locally in Chrome using
[Karma](https://karma-runner.github.io) test runner.

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via
[Protractor](http://www.protractortest.org/).

## Deploying web app to development Firebase instance

Run `ng deploy`.

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular
CLI README](https://github.com/angular/angular-cli/blob/master/README.md).
