# Ground Firestore Config

This directory contains the configuration for new Firestore instances. After following instructions in the main [../](README.md), the config can be deployed as follows.

If not already logged in, run:

```
$ firebase login
```

Once authenticated, select your Firebase project with:

```
$ firebase use --add gnddemo1
```

Then, deploy config with:

```
$ firebase deploy --only firestore
```
