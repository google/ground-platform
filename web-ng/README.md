# Ground Web Console in Angular (WIP)

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

Create a file in `environments/.backend-config.json` with the path to your
hosted or local [Firebase Cloud Functions](https://firebase.google.com/products/functions) instance:

```json
{
  "cloudFunctionsHost": "some-subdomain.cloudfunctions.net",
  "offlineBaseMapSources": [{ "url": "" }]
}
```

If you don't yet have [Ground Cloud Functions](../functions) deployed, you
may set `cloudFunctionsHost` to `'localhost'` for now; server-side operations
like bulk CSV export will not be available.

## Dev Environment Setup

Install Node.js using nvm:

```
$ nvm install 13 && nvm use 13
```

Install ng CLI using npm:

```
$ npm install -g @angular/cli
```

Install required npm dependencies:

```
$ npm install
```

This last step will fail if you haven't yet following instructions above in
[Add Google Maps API key](#Add Google Maps API key).

## Development server

Run `ng serve` for a dev server. Navigate to http://localhost:4200/. Add
`project/<project_name>` as a suffix to the url to see an existing project interface
in your browser. Alternatively, go to http://localhost:4200/project/new for a
new project.
The app will automatically reload if you change any of the source files.

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
