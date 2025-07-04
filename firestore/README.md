# Ground Firestore Config

This directory contains configuration files for new Firestore instances. Complete the following steps to deploy the Firestore configuration:

> **Important:** Before completing the steps listed in this document, follow the instructions provided in [../functions](../functions/README.md) to set up your development environment. 

1. Download and install dependencies.

    ```sh
    npm ci
    ```

2. Log into Firebase:

    ```sh
    npx firebase login
    ```

3. Deploy the Firestore configuration:

    ```sh
    npx firebase deploy --only firestore --project <firebase-project>
    ```
