# Ground Cloud Functions

- [Environment Setup](#environment-setup)
- [Development Workflow](#development-workflow)

## Environment setup

The following sections describe how to set up your development environment to
modify, run, and deploy Ground Cloud Functions source code.

Before proceeding, follow instructions in [../README.md](../README.md). Then, download project
dependencies with:

```
$ npm install
```

### Set up Firebase

Complete the following steps to set up Firebase before running or deploying
Cloud Functions.

## Development workflow

The following steps are useful for testing or examining modifications to the
Ground Cloud Functions source code.

1. Confirm you're logged into firebase:

   ```
   $ npm run login
   ```

2. Test Cloud Functions in a browser using a local emulator with:

   ```
   $ npm run emulators
   ```

   Alternatively, you can test functions from a command-line shell using:

   ```
   $ npm run shell
   ```

   For more information on using the Firebase emulator and shell, see
   <https://firebase.google.com/docs/functions/local-emulator>.

### Deploy to Firebase

To deploy cloud functions to Firebase run:

    $ npm run deploy -- --project <project-name>
