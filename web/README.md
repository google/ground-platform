# Ground Web Console

* [Environment Setup](#environment-setup)
* [Development Workflow](#development-workflow)
* [Deployment](#deployment)

## Environment Setup

The following sections describe how to set up your development environment to
work on Ground Web Console source code.

> **Note:** All instructions in this guide assume `gnd-cloud/web/` is your current working directory.

### Node.js 

> **Note**: This guide recommends using Node Version Manager (nvm) to install and manage versions
> of Node.js and assumes nvm is installed. For more information on nvm, as well
> as installation instructions, see: <https://github.com/creationix/nvm#installation>

To set up Node.js:

1. Install the required version of Node.js.

    ```
    $ nvm install 10.3.0
    ```

    > **Note:** To set this version of Node.js as your default run
    > `nvm alias default 10.3.0`

### Firebase 

To set up Firebase:

1. Visit <https://firebase.corp.google.com/> and create and/or select your development instance.
2. Select **Project Overview**.
3. Select the **+ Add app** chip displayed under the main heading, then select the **web icon** (**&lt;/&gt;**).
4. Copy the second key/value pair that appears into a new file,
  `src/.firebase-config.js `, surrounding it with the following export block:

    ```javascript
    export default {
      apiKey: "<api-key>",
      authDomain: "<project-name>.firebaseapp.com",
      databaseURL: "https://<project-name>.firebaseio.com",
      projectId: "<project-name>",
      storageBucket: "<project-name>.appspot.com",
      messagingSenderId: "1234567890"
    };
    ```
    Where:
    * `<api-key>` is your Firebase api key, see <https://cloud.google.com/docs/authentication/api-keys>
    * `<project-name>` is the name of your Firebase project.

5. Install firebase tools:

    ```
    $ npm install -g firebase-tools
    ```

### Add Google Maps API Key

1. If you don't already have one, generate a new API key at <https://developers.google.com/maps/documentation/android-sdk/signup#quick-guide>

    > **Note:** Alternatively, you can access an existing project's key via [Google Cloud
    > Platform](https://console.cloud.google.com/) under APIs \> Services \>
    > Credentials.

2. Write the key to `src/.google-maps-config.js` as follows:

    ```html
    export default {
      apiKey: "YOUR_API_KEY"
    }
    ```
    Where `YOUR_API_KEY` is your Google Maps API key.

## Developer workflow

### Select Node.js version

If you've changed Node.js versions (e.g., to work on the Ground Firebase Cloud 
Functions), you can switch back manually by running:

```
$ nvm use 10.3.0
```


### Running the Ground Web Console

To build the Ground Web Console, complete the following steps.

1. Download required dependencies:

    ```
    $ npm install
    ```

2. Run the app locally: 

    ```
    $ npm start
    ```

You should then see the demo project running locally, for example, at:

  `http://127.0.0.1:3000/p/project_id`

Where `project_id` is the id of a Ground project defined in the Cloud Firestore database, or `:new` for a new, unsaved Ground project.

## Deployment

1. Build the Ground Web Console:

    ```
    $ npm run build
    ```

2. Log into Firebase:

    ```
    $ firebase login
    ```

3. Select your Firebase project:

    ```
    $ firebase use --add <project-name>
    ```
    Where `project-name` is the name of a project associated with your account.

4. Deploy the Ground Web Console build to Firebase web hosting:

    > **Note:** You must deploy the web console from the repository's root
    > directory. 

    ```
    $ cd <path/to/ground>/gnd-cloud
    $ pushd .. && firebase deploy --only hosting && prod
    ```
    Where `<path/to/ground>` is the path to the gnd-cloud repository root on your
    system.
