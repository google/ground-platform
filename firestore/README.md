# Ground Firestore Config

This directory contains configuration files for new Firestore instances. Complete the following steps to deploy the Firestore configuration:

> **Important:** Before completing the steps listed in this document, follow the instructions provided in [../functions](../functions/README.md) to set up your development environment. 

1. Log into Firebase:

    ```
    $ firebase login
    ```

2. Select your Firebase project:

    ```
    $ firebase use --add <project-name>
    ```
    Where `<project-name>` is the name of a project associated with your
    Firebase account.

3. Deploy the Firestore configuration:

    ```
    $ firebase deploy --only firestore
    ```
