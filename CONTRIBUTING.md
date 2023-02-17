# Contributing to Ground

First-time setup:

1. Read the [Code of Conduct](#code-of-conduct)
2. Read and accept the [Contributor License Agreement](#contributor-license-agreement-cla)
3. [Clone the repo](#cloning-the-repo)
4. [Set up your environment](#seting-up-your-environment)
5. [Build and run the app](#building-and-running-the-app)

Developing Ground:

1. [Claim an issue](#claiming-an-issue)
2. Create a new branch
3. Iterate on your contribution
4. Create a pull request

## Code of Conduct

Help us keep the Ground project open and inclusive. Please read and follow 
[Google's Open Source Community Guidelines](https://opensource.google.com/conduct/)
when contributing.

## Contributor License Agreement (CLA)

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution;
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Cloning the repo

The following instructions describe how to fork this repository in order 
to contribute to the Ground codebase. If you are a maintainer on the Ground
core team, follow the instructions under [Maintainers](#maintainers). Other
community contributors should proceed as described in
[Community contributors](#community-contributors).

### Community contributors

1. Fork this repository, see <https://help.github.com/articles/fork-a-repo/>.

2. Clone your fork:
    
    ```shell
    git clone https://github.com/<user>/ground-platform.git`
    ```
    
    Where `<user>` is your GitHub username.

3. To be able to pull changes from upstream, add the base repository as a remote:
    
    ```shell
    git remote add upstream https://github.com/google/ground-platform.git
    ```

   You can then pull future upstream changes into your local clone with `git pull upstream master`.

### Maintainers

Maintainers may push directly to branches in the `google/ground-platform`, and
so they do not need to fork the repo. Simply clone the repo with:
    
```shell
git clone https://github.com/google/ground-platform.git`
```

## Seting up your environment

> **Note**: This guide assumes nvm (Node Version Manager) will be used to
> install and manage Node.js versions. For more information on nvm,
> including installation instructions, see
> https://github.com/creationix/nvm#installation>

First, install Node.js:

```shell
nvm install 16
```

## Building and running the app

### Running locally

To get up and running quickly, you can run the web app and Cloud Functions against the local
Firebase emulator.

To install dependencies, build, and start in the emulator:

```shell
npm install && npm run build:local && npm run start:local
```

Once the local server is ready, the app will be available at http://localhost:5000.

**Note**: The local build variant does not need API keys to run. Warnings related to missing
API keys are expected.

### Running against dev server

TODO

------

# WIP

## Claiming an issue

Before starting work on a change, comment on one of the [open issues](https://github.com/google/ground-platform/issues?utf8=%E2%9C%93&q=is%3Aissue+is%3Aopen)
saying you'd like to take it on. If one does not exist, you can also
[create one here](https://github.com/google/ground-platform/issues/new).

## Submitting a PR

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

Be sure to replace template fields that are automatically added to the PR, especially
the "Fixes #<issue no>" field.

<!-- 4. Follow the instructions in the README files of each of the directories listed under [About this Repository](README.md#about-this-repository) section of this readme to set up your development environment. -->

## Development workflow

We strongly recommend creating a branch for each change. See [Basic Branching and Merging](https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging) for details.

Maintainers should create branches with names in the form `<username>/<issue-no>/<short-text>`. For example:

```shell
git checkout -d gino-m/1234/fix-save-button
```

After you have forked and cloned the repository, use the following steps to
make and manage changes. After you have finished making changes, you can 
submit them to the base repository using a pull request. 

1. Pull changes from the base repository's master branch:
    
    `git pull upstream master`

1. Create a new branch to track your changes:
    
    `git checkout -b <branch>`
    
    Where `<branch>` is a meaningful name for the branch you'll use to track
    changes.

1. Make and test changes locally.

1. Add your changes to the staging area:
    
    `git add <files>`
    
    Where `<files>` are the files you changed.
    
    > **Note:** Run `git add .` to add all currently modified files to the staging area.

1. Commit your changes:
    
    `git commit -m <message>`
    
    Where `<message>` is a meaningful, short message describing the purpose of
    your changes.

1. Pull changes from the base repository's master branch, resolve conflicts if
   necessary:
      
    `git pull upstream master`

1. Push your changes to your github account:
    
    `git push -u origin <branch>`
    
    Where `<branch>` is the branch name you used in step 2.

    **Note**: If you have two factor authentication enabled, make sure to use
    your [Personal Access Token](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line) instead of your password.


    Alternatively, you can [connect to GitHub via SSH](https://help.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh) instead of HTTPS and use
    a public/private key pair for authentication.

1. Create a [pull request](https://help.github.com/articles/about-pull-requests/) to have your changes reviewed and merged into the base 
repository. Reference the [issue](https://github.com/google/ground-platform/issues) your changes resolve in either the commit message for your changes or in your pull request. 

    > :exclamation: Any subsequent changes committed to the branch you used
    > to open your PR are automatically included in the PR. If you've opened a
    > PR but would like to continue to work on unrelated changes, be sure to
    > start a new branch to track those changes.

    For more information on creating pull requests, see <https://help.github.com/articles/creating-a-pull-request/>. 
    
    To learn more about referencing issues in your pull request or commit messages, see <https://help.github.com/articles/closing-issues-using-keywords/>.

1. Celebrate!


## Getting started
