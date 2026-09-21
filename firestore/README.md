<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

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
