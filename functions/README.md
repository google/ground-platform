# Ground Cloud Functions

* [Environment Setup](#environment-setup)
* [Development Workflow](#development-workflow)

## Environment setup

The following sections describe how to set up your development environment to
modify, run, and deploy Ground Cloud Functions source code. 

**Note:** This guide assumes `gnd-cloud/functions/` is your current working directory.

## Node.js setup

This guide recommends using Node Version Manager (nvm) to install and manage versions
of Node.js, and assumes nvm is installed. For more information on nvm, as well
as installation instructions, see: <https://github.com/creationix/nvm#installation>

Firestore Cloud Functions currently require version 6.11.5 of Node.js.

1. Install the required version of Node.js:

    ```
    $ nvm install 6.11.5
    ```
  
    To set this as the default version run:
  
    ```
    $ nvm alias default 6.11.5
    ```

2. Download project dependencies:

    **Note:** Ensure `gnd-cloud/functions` is your current working directory
    before installing dependencies.
  
    ```
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
  
    For more information on using `npm install`, see <https://docs.npmjs.com/cli/install>
  
    For more information on firebase-tools, see <https://github.com/firebase/firebase-tools>

2. Log into Firebase:

    ```
    $ firebase login
    ```
  
    After you run the `login` command, follow the prompts in your browser  or terminal to authenticate your firebase account.

3. Select your Firebase project:

    ```
    $ firebase use --add <project-name>
    ```
  
    Where `<project-name>` is the name of a project associated with your firebase
    account.

4. Fetch the firebase database config:

    ```
    $ firebase functions:config:get > .runtimeconfig.json
    ```

### Set up a service account and keys

In order to synchronize data with Google Sheets, Ground uses a special Google Cloud Service Account. This prevents project creators from needing to authorize Ground to read and write files in their Google Drive files each time a spreadsheet is linked. Instead, project creators can simply grant the service account Edit access to the linked spreadsheet.

1. Create a new service account:
  
   * Follow the steps detailed at <https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating_a_service_account> to create a new service account.
   * Make a note of the email address of the service account. Users need this account to allow access to their spreadsheets.

2. Create a new private key:

   * Follow the steps detailed at <https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys>
   * When prompted for key type, select "JSON". Download and save the resulting key file in `service-account-secret.json`. As the name implies, this file should be kept secret, and should not be checked into source control.

After the service account is created, you can regenerate keys under
`Actions > Create key > JSON` at `https://console.cloud.google.com/apis/credentials?project=<project-name>` where `<project-name>` is the name of the project associated with your firebase account.

## Development workflow

The following steps are useful for testing or examining modifications to the
Ground Cloud Functions source code. 

1. Select a Node.js version: 

    ```
    $ nvm use 6.11.5
    ```

2. Confirm you are logged into firebase:

    ```
    $ firebase login
    ```

3. Run cloud functions locally to test changes:

    ```
    $ firebase functions:shell
    ```
  
    For more information on using the Firebase shell, see <https://firebase.google.com/docs/functions/local-emulator>.

### Deploy to Firebase

To deploy cloud functions to Firebase run:

    ```
    $ firebase deploy --only functions
    ```
