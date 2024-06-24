# Ground Firestore Config

This directory contains configuration files for new Firestore instances. Complete the following steps to deploy the Firestore configuration:

> **Important:** Before completing the steps listed in this document, follow the instructions provided in [../functions](../functions/README.md) to set up your development environment. 

1. Log into Firebase:

    ```
    $ npx firebase login
    ```

2. Deploy the Firestore configuration:

    ```
    $ npx firebase deploy --only firestore --project <firebase-project>
    ```
