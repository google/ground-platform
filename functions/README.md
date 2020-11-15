# Ground Cloud Functions

- [Environment Setup](#environment-setup)
- [Development Workflow](#development-workflow)

## Environment setup

The following sections describe how to set up your development environment to
modify, run, and deploy Ground Cloud Functions source code.

> **Note:** All instructions in this guide assume `ground-platform/functions/` is your current working directory.

### Node.js

> **Note**: This guide recommends using Node Version Manager (nvm) to install and manage versions
> of Node.js and assumes nvm is installed. For more information on nvm, as well
> as installation instructions, see: <https://github.com/creationix/nvm#installation>

Firestore Cloud Functions currently require version 6.11.5 of Node.js.

1. Install a [supported version](https://firebase.google.com/docs/functions/manage-functions#set_nodejs_version) of Node.js:

   ```
   $ nvm install <version>
   ```

   To set this as the default version run:

   ```
   $ nvm alias default <version>
   ```

2. Download project dependencies:

   ```
   $ cd functions
   $ npm install
   ```

### Set up Firebase

Complete the following steps to set up Firebase before running or deploying
cloud functions.

1. Install [Firebase CLI](https://firebase.google.com/docs/cli/) globally to your system:

   **Note:** This step is required even if you've already installed the tools for use with the web/ component.

   ```
   $ npm install -g firebase-tools
   ```

   For more information on using `npm install`, see
   <https://docs.npmjs.com/cli/install>

   For more information on firebase-tools, see
   <https://github.com/firebase/firebase-tools>

2. Log into Firebase:

   ```
   $ firebase login
   ```

   After you run the `login` command, follow the prompts in your browser or
   terminal to authenticate your Firebase account.

3. Select your Firebase project:

   ```
   $ firebase use --add <project-name>
   ```

   Where `<project-name>` is the name of a project associated with your Firebase
   account.

4. Fetch the Firebase database config:

   ```
   $ firebase functions:config:get > .runtimeconfig.json
   ```

### Set up a service account and keys

Ground uses a Google Cloud Service Account to manage and synchronize data in Google Sheets.

1. Create a new service account:

   - Follow the steps detailed at <https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating_a_service_account> to create a new service account. If you already
     create a service account for your project and are trying to generate a new key, you can skip this step.

2. Create a new private key:

   - Follow the steps detailed at <https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys>
   - When prompted for key type, select "JSON". Download and save the resulting key file in `service-account-secret.json`. As the name implies, this file should be kept secret, and should not be checked into source control.

## Development workflow

The following steps are useful for testing or examining modifications to the
Ground Cloud Functions source code.

1. Select a Node.js version:

   ```
   $ nvm use <version>
   ```

2. Confirm you're logged into firebase:

   ```
   $ firebase login
   ```

3. Test Cloud Functions in a browser using a local emulator with:

   ```
   $ firebase emulators:start
   ```
   
   Alternatively, you can test functions from a command-line shell using:

   ```
   $ firebase functions:shell
   ```


   For more information on using the Firebase shell, see
   <https://firebase.google.com/docs/functions/local-emulator>.

### Deploy to Firebase

To deploy cloud functions to Firebase run:

    $ firebase deploy --only functions
