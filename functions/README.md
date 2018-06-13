# Ground Cloud Functions

## First-time developer setup

This guide assumes `functions/` is your current working directory.

## Install Node.js

Firestore Cloud Functions requires a older version of Node.js that Polymer:

```
$ nvm install 6.11.5
```

You can set this as the default version with:

```
$ nvm alias default 6.11.5
```

Next, install [Firebase CLI](https://firebase.google.com/docs/cli/) globally to your system (`-g`) using Node Package Manager (NPM):

```
$ npm install -g firebase-tools
```

This is required even if you've already installed the tools for use with the web/ component.

### Downloading dependencies

Download Node.js dependencies required by Ground Cloud Functions with:

```
$ npm install
```

### Set up Firebase

Follow these steps to allow running the local shell or to deploy Firebase functions.

You can then log into Firebase with:

```
$ firebase login
```

Once authenticated, select your Firebase project with:

```
$ firebase use --add gnddemo1
```

Before running the first time, fetch the database config with:

```
$ firebase functions:config:get > .runtimeconfig.json
```

### Set up service account and keys

In order to synchronize data with Google Sheets, Ground uses a special Google Cloud Service Account. This prevents project creators from needing to authorize Ground to read and write files in their Google Drive files each time a spreadsheet is linked. Instead, project creators can simply grant the service account Edit access to the linked spreadsheet.

To create a new service account:

  https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating_a_service_account

Select the "Console" tab for instructions on creating the account through the Google Cloud Console UI.

Make a note of the email address of the service account. This is the account users will need to allow access to their spreadsheets.

Next, create a new private key:

  https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys

When prompted for key type, select "JSON". Download and save the resulting key file in `service-account-secret.json`. As the name implies, this file should be kept secret, and should not be checked into source control.

Once the account is created, keys can regenerated again in future under Actions > Create key > JSON at:

  https://console.cloud.google.com/apis/credentials?project=gnddemo1

Where `gnddemo` is your project name.

## Developer workflow

### Select Node.js version

You can switch versions manually when opening a new shell with:

```
$ nvm use 6.11.5
```

### Running Cloud Functions locally

You can then run the local functions emulator with:

```
$ firebase functions:shell
```

Instructions for using the shell can be found at https://firebase.google.com/docs/functions/local-emulator.

## Deploy to Firebase hosting

To deploy theCloud Functions to Firebase:

```
$ firebase deploy --only functions
```

