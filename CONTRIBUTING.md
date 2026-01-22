# Contributing to Ground

This guide describes how to contribute to the Ground open-source project. Please read it in its entirety before you begin.

First-time setup:

1. Read the [Code of Conduct](#code-of-conduct)
2. Read and accept the [Contributor License Agreement](#contributor-license-agreement-cla)
3. [Clone the repo](#cloning-the-repo)
4. [Set up your environment](#seting-up-your-environment)
5. [Build and run the app](#building-and-running-the-app)

Contributing to Ground:

1. [Claim an issue](#claiming-an-issue)
2. [Create a new branch](#creating-a-branch)
3. Iterate on your change
4. [Create a pull request](#creating-a-pull-request)

**Note**: This guide assumes you are comfortable working with Git and GitHub. If you're new to Git and/or GitHub, check out [Quickstart - GitHub Docs](https://docs.github.com/en/get-started/quickstart) to help you get started.

## First time setup

### Code of Conduct

Help us keep the Ground project open and inclusive. Please read and follow [Google's Open Source Community Guidelines](https://opensource.google.com/conduct/) when contributing.

### Contributor License Agreement (CLA)

Contributions to this project must be accompanied by a Contributor License Agreement. You (or your employer) retain the copyright to your contribution; this agreement gives us permission to use and redistribute your contributions as part of the project. Head over to <https://cla.developers.google.com/> to see your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one (even if it was for a different project), you probably don't need to do it again.

### Cloning the repo

The following instructions describe how to fork this repository in order to contribute to the Ground codebase. If you are a maintainer on the Ground core team, follow the instructions under [Maintainers](#maintainers). Community contributors should instead proceed as described in [Community contributors](#community-contributors).

#### Maintainers

Maintainers may push directly to branches in the `google/ground-platform`, and
so they do not need to fork the repo. Simply clone the repo with:
    
```shell
git clone https://github.com/google/ground-platform.git
```

#### Community contributors

1. [Fork this repository](https://github.com/google/ground-platform/fork).

2. Clone the new fork to your local device:
    
    ```shell
    git clone https://github.com/<user>/ground-platform.git`
    ```
    
    Where `<user>` is your GitHub username.

3. To be able to pull changes from upstream, add the base repository as a remote:
    
    ```shell
    git remote add upstream https://github.com/google/ground-platform.git
    ```

   You can then pull future upstream changes into your local clone with `git pull upstream master`.

### Setting up your environment

> **Note**: This guide assumes nvm (Node Version Manager) will be used to
> install and manage Node.js versions. For more information on nvm,
> including installation instructions, see
> https://github.com/creationix/nvm#installation>

Install and use Node.js using:

```shell
nvm install 20
nvm use 20
```

Install required global tools:
```shell
npm install -g firebase-tools nx pnpm
```


### Building and running the app

#### Running locally

To get up and running quickly, you can run the web app and Cloud Functions,locally using the Firebase Local Emulator Suite.

Install dependencies:
```shell
pnpm install
```
Build and start the local Firebase Emulator and Angular dev server:
``` shell
nx start
```

Once started:
- App: http://localhost:4200
- Firebase Emulator UI: http://localhost:4000

Changes to the web app in `web/` and to Cloud Functions in `functions/` are automatially rebuilt and reloaded on save.

**Note**: The local build variant does not require API keys to run. Warnings related to missing API keys are expected. Authentication is also disabled.

The local emulator is preloaded with a demo survey.
Run `nx export` to save updated demo data for the next run.

### Run against live staging environment

#### Staging environment (maintainers only)

Core maintainers can run the app against the live staging environment with:

```shell
nx run web:serve:staging
```

#### Deploying to staging
```shell 
npx firebase login
nx run deploy:staging
```
#### Deploying to your own production Firebase project

Override firebaseConfig in environment.production.ts

Update the Firebase project ID placeholder in package.json

Deploy with:
``` shell
nx run deploy:production
```

 
## Developing Ground

### Claiming an issue

Before you begin work on a change, comment on one of the [open issues](https://github.com/google/ground-platform/issues?utf8=%E2%9C%93&q=is%3Aissue+is%3Aopen) saying you'd like to take it on. If one does not exist, you can also [create one here](https://github.com/google/ground-platform/issues/new).

### Creating a branch

We strongly encourage contributors to create a separate branch for each pull request. Maintainers working directly in `google/ground-platform` should create branches with names in the form `<username>/<issue-no>/<short-desc>`. For example:

```shell
git checkout -b <user-id>/1234/fix-save-button
```

### Creating a pull request

All submissions require review and approval by at least one maintainer. The same rule also applies to mainatiners themselves. We use GitHub pull requests for this purpose.

When creating a new pull request from one of the provide templates, be sure to replace template fields, especially the PR title and `Fixes #<issue no>` field in the comment field.

For more information about creating pull requests, see <https://help.github.com/articles/creating-a-pull-request/>. To learn more about referencing issues in your pull request or commit messages, see <https://help.github.com/articles/closing-issues-using-keywords/>.

    > :exclamation: Any subsequent changes committed to the branch you used
    > to open your PR are automatically included in the PR. If you've opened a
    > PR but would like to continue to work on unrelated changes, be sure to
    > start a new branch to track those changes.
