---
title: "Build and Deploy Ground from Source"
layout: default
---

# Build and Deploy Ground from Source

The Ground platform consists of a web app and an Android mobile app, both of
which use Firebase as a common backend. In order to use Ground, you must build
and deploy an instance of Ground using the source files hosted on GitHub. This
only needs to be done once per organization owning the Ground data; once
deployed, Ground can host multiple surveys, each with their own permissions and
sharing rules.

> Note: For more information about Ground, see the Github repos:
> [ground-platform](https://github.com/google/ground-platform) and
> [ground-android](https://github.com/google/ground-android).

## Setup overview

In order to set up and install Ground you need to complete the following steps:

1. [Create and configure a Firebase survey](#create-and-configure-firebase)
2. [Configure and build the web app](#configure-and-build-web)
3. [Configure and build the Android app](#configure-and-build-android)
4. [Share Ground web login details and distribute the Android app](#share-and-distribute)


### Prerequisites

Before you get started, you’ll need the following:

- A [Google Cloud Billing account](https://cloud.google.com/billing/docs/how-to/manage-billing-account) 
- Tools required to build and configure your Ground platform instance and Android app:
  - [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
  - [nvm](https://github.com/nvm-sh/nvm)
  - [Android Studio](https://developer.android.com/studio)
- A basic understanding of command-line tools and updating configuration files
  using a text editor. You do not need to be proficient in Firebase, Angular, or
  Android development unless you want to modify the standard Ground components.
  However, you should be comfortable using developer tools.


## 1. Create and configure a Firebase survey {#create-and-configure-firebase}

The Firebase survey includes the configuration for your instance of Ground, as
well as a [Cloud Firestore](https://firebase.google.com/docs/firestore)
database, [Cloud Functions](https://firebase.google.com/docs/functions),
[Authentication](https://firebase.google.com/docs/auth), [Cloud
Storage](https://firebase.google.com/docs/storage), and
[Crashlytics](https://firebase.google.com/docs/crashlytics). Firebase surveys
are technically also Google Cloud
surveys. However, we recommend creating a new Firebase survey for your Ground
instance, rather than using an existing Cloud survey.

1. Go to
   [https://console.firebase.google.com/](https://console.firebase.google.com/)
   and log into your Firebase account.
1. Click **Create a survey**.
1. Enter a survey name, accept the terms, and click **Continue**.
1. Make sure that _Enable Google Analytics for this survey_ is selected and click
**Continue**. Ground does not currently use Google Analytics, but it may be useful
in future versions.
1. Create or select an Analytics account, accept the terms, and click **Create survey**.

### Create a Firestore database

Cloud Firestore is used for storing Ground data, which is organized into a
hierarchy of collections and documents. To learn how Ground data is represented
in Cloud Firestore, see [Cloud Firestore
Representation](https://github.com/google/ground-platform/wiki/Cloud-Firestore-Representation)
page in the Ground platform wiki. 

1. From the left nav on the Firebase survey page, select **Build > Firestore Database**.
1. Click **Create database**.
1. Select **Start in production mode** and click **Next**.
1. Select a Cloud Firestore location. For more information about selecting a
   location, see [Location
   considerations](https://cloud.google.com/storage/docs/locations#considerations).
1. Click **Enable**.

### Enable Google sign-in

Ground relies on Firebase for its _Sign in with Google_ functionality.

1. From the left nav on the Firebase survey page, select **Build > Authentication**.
1. Click **Get started**.
1. In the _Sign-in providers_ list, select **Google**.
1. Click the **Enable** toggle switch.
1. Enter a public-facing login name, which will appear on the sign-in dialog.
1. Select an email address for sign-in-related issues.
1. Click **Save**.

### Add another survey owner (optional)

If you’d like to allow someone else to be able to administer and modify the
Firebase survey, you can grant them “owner” access as follows:

1. From the left nav on the Firebase survey page, click the settings gear next
   to _Survey Overview_ and select **Users and permissions**.
1. Click **Add member**.
1. Enter the email address of the person you would like to add as a survey owner.
1. From the _Role(s)_ list, select **Owner**.
1. Click **Add member**.

> Note: This step has no effect on who can view which Ground surveys in the web
and Android app. Firebase permissions only control who can administer the
backend via the command-line and Firebase console.

### Grant users access by creating a passlist {#create-passlist}

Ground uses a passlist to control which users can sign into Ground surveys
hosted in your new Firebase survey. This passlist is a collection of documents
within the survey’s Firestore database where the IDs of the documents are the
email addresses of the users in the passlist.

1. From the left nav on the Firebase survey page, select **Build > Firestore Database**.
1. Click **Start collection**.
1. For the _Collection ID_, enter **passlist**, and click **Next**.
1. Add the first user to the collection by entering the user’s email address as
   the _Document ID_. Add at least one field-value pair of your choosing and click
   **Save**. Typical values might include “addedBy”, “reason”, “org”, etc.

> Note: If you would like to add all email addresses for a specified domain to the
passlist, you can add a document that uses a [regular
expression](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions)
to grant access
to all email addresses matching a pattern. To do this, create a document in the
passlist collection with the ID **regexp** containing a single field, also **regexp**,
the value of which contains a regular expression matching email address of users
allowed to create and/or access Ground surveys.  For example, to add all users
for the _@example.com_ domain, you would enter `.*@example[.]com` as the regexp in
the regexp document.

### Enable the Resize Images Extension

1. From the left nav on the Firebase survey page, click **Extensions**.
1. Under _Resize Images_, click **Install**.
1. Click **Next**.
1. Click **Next**.
1. Click **Next**.
1. In the _Configure extension_ section, specify the following values:
  - _Size of reduced images_: **200x200**
  - _Deletion of original file_: **No**
  - _Paths that contain images you want to resize (Optional)_: **/user-media**
  - _Convert image to preferred types_: **original**
1. Click **Install extension**.

### Change the survey billing plan to Blaze

By default, Firebase surveys use the free _Spark_ plan. However, Ground requires
functionality, such as Cloud Functions, which are not included in the _Spark_
plan, so you need to change your billing plan to the pay-as-you-go _Blaze_ plan.

1. From the left nav on the Firebase survey page, click the settings gear next
   to _Survey Overview_ and select **Usage and billing**.
1. Click **Details & settings**.
1. Click **Modify plan**.
1. Under _Blaze Pay as you go_, click **Select plan**.
1. Click **Purchase**.
1. Optional: If you want to be notified when your usage meets certain thresholds:
    1. Click **Set a budget alert**.
    1. Enter an amount for the alert and click **Set budget alert**.

> Note: For more information on Firebase pricing, see
> [https://firebase.google.com/pricing](https://firebase.google.com/pricing).

### Add the Android app to your Firebase survey {#add-android-app}

In order for the Ground Android app to access Firebase services, you must first
add it to your Firebase survey. In a later step you will use the resulting keys
to build the app binary from source.

1. From the left nav on the Firebase survey page, click **Survey Overview**.
1. Under _Get started by adding Firebase to your app_, click the Android icon.
1. Under _Register app_, enter the following values:
    1. _Android package name_: **com.google.android.gnd**
    1. _App nickname_: Enter any nickname you want
    1. _Debug signing certificate SHA-1_: Leave blank
1. Click **Register app**.
1. Click **Download google-services.json** and save the file to add later to your Android Studio survey.
1. Click **Next**.
1. Click **Next**.
1. Click **Continue to console**.

### Add the web app to your Firebase survey {#add-the-web-app}

Similar to the configuration for the Android app described in the previous
section, you must also add the Ground web app to your Firebase survey.

1. From the left nav on the Firebase survey page, click **Survey Overview**.
1. Under _Get started by adding Firebase to your app_, click the web icon. 
> Note: If you do not see the app icons, click **Add app**, then click the web icon.
1. In the _App nickname_ field, enter any nickname you want to use for the web app.
1. Check the **Also set up Firebase Hosting for this app** box.
1. Click **Register app**.
1. Click **Next**.
1. Click **Next**.
1. Click **Continue to console**.
1. From the left nav on the Firebase survey page, click the settings gear next
   to _Survey Overview_ and select **Survey settings**.
1. In the _Your apps_ section, scroll down to the web app you created.
1. In the _SDK setup and configuration_ section, select **Config**.
1. Copy the code snippet and save it to a text file to add later to your web app configuration.

### Enable the Google Maps APIs for your Firebase survey {#enable-maps-api}

Both the Android and web apps for Ground use the Google Maps API, so you need to
enable the Maps SDK for your survey and retrieve the API keys to use with your
apps.

1. Go to the Google Cloud Console at
   [http://console.cloud.google.com](http://console.cloud.google.com).
1. If the selected survey is not the survey you created in Firebase for
   Ground, click survey name in the top menu bar and select your Ground
   survey.
1. From the left nav, select **APIs & Services > Library**.
1. Select **Maps SDK for Android**.
1. Click **Enable**.
1. From the left nav, select **APIs**.
1. In the _Additional APIs_ section, select **Maps JavaScript API**.
1. Click **Enable**.
1. From the left nav, select **Credentials**.
1. In the _API Keys_ section, copy the keys for _Android key (auto created by
   Firebase)_ and _Browser key (auto created by Firebase)_ and save them to a
   text file to add later to your Ground app.

## 2. Configure and build the web app {#configure-and-build-web}

Once you have your Firebase survey configured, you’re ready to build and deploy
the Ground web app. Like the Android app, the source files for the web app are
stored on GitHub. 

1. In a terminal window, in the directory where you want to download the web app
   source code, run **`git clone
   https://github.com/google/ground-platform.git`**
2. Create a new file for the Firebase configuration:
   **ground-platform/web/src/environments/.firebase-config.ts**
3. Add code snippet that you copied from the config page in [Add the web app to
   your Firebase survey](#add-the-web-app).
4. Adding **`export`** before **`const`**, the contents of the file should look roughly like this:

    ```
    export const firebaseConfig = {
        apiKey: 'soMeReallYlOngApIkeyWouLdGoHere123',
        authDomain: 'my-app.firebaseapp.com',
        databaseURL: 'https://my-app.firebaseio.com',
        surveyId: 'my-app',
        storageBucket: 'my-app.appspot.com',
        messagingSenderId: '12345678',
        appId: '1:12345678:web:abc123etcetc',
    };
    ```

5. In a terminal window, from the _ground-platform_ directory:
    1. Run **`nvm install 14 && nvm use 14`**
    2. Run **`npm install -g @angular/cli firebase-tools`**
    3. Run **`firebase use &lt;your-Firebase-survey-id&gt;`**
    4. Run **`./build.sh`**
    5. Run **`firebase deploy`**
6. Once deployment is complete, test the app by going to **https://&lt;your-Firebase-survey-name&gt;.web.app**

## 3. Configure and build the Android app {#configure-and-build-android}

After deploying the Ground web app, you can build the Android app. Like the web
app, the source files for the Android app are stored on GitHub. You can download
the source files and then use Android Studio to build the application. After you
build the app, you also need to add the certificate fingerprint to your Firebase
survey, so that the Ground services can authenticate your app.

### Build the Android app

1. In a terminal window, in the directory where you want to download the Android
   app source code, run **`git clone
   https://github.com/google/ground-android.git`**
1. Within **ground-android/gnd/src/** create a **debug/** directory.
1. Add the **google-services.json** that you downloaded in [Add the Android app
   to your Firebase survey](#add-android-app) to
   **ground-android/gnd/src/debug/**.
1. Within **ground-android/gnd/**, create a **secrets.properties** file and add
   the following text (using the Android API key that you copied in [Enable the
   Google Maps APIs for your Firebase survey](#enable-maps-api)):
   **GOOGLE_MAPS_API_KEY=&lt;your-Android-Maps-API-key&gt;**
1. Open Android Studio.
1. Select **File > New > Import Survey**.
1. Select the **ground-android** directory and click **Open**.
1. At the bottom-right corner of the window, click **Event Log**.
1. Click **Install required plugins.**
1. Click **OK**.
1. Click **Accept**.
1. Click **Restart to activate plugin updates**.
1. After restarting, at the bottom-right corner of the window, click **Enable for this survey**.
1. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
1. In the _Build APK(s)_ notification that appears at the bottom-right corner of
   the window when the build completes, click **locate** to open the directory
   that contains the app APK.

For information on distributing and installing the APK, see [Distributing the Android app](#distributing).

### Retrieve and add the certificate fingerprint to your Android app configuration in Firebase

1. Open a terminal window and navigate to your home directory.
1. Run: **`keytool -list -v -keystore .android/debug.keystore -alias
   androiddebugkey -storepass android -keypass android`**
1. Copy the **SHA-1** certificate fingerprint value.
1. From the left nav on the Firebase survey page, click the settings gear next
   to _Survey Overview_ and select **Survey Settings**.
1. In the _Your apps_ section, select your Android app.
1. Click **Add fingerprint**.
1. Paste the **SHA-1** value that you retrieved from the keystore.
1. Click **Save**.

## 4. Share Ground web login details and distribute the Android app {#share-and-distribute}

Once you configure and build the Ground apps, you can share them with your survey collaborators. 

> Note: Before sharing the apps, make sure to add the collaborators’ email
> addresses or domains to the [passlist](#create-passlist) in Firebase.

### Sharing the web app

1. Go to **https://&lt;your-Firebase-survey-name&gt;.web.app**
1. Create a new survey.
1. In the top-right corner of the screen, click **Share**.
1. Add collaborators:
    1. Enter the collaborator’s email address.
    1. Select a role.
    1. Click **Add**.
1. Click **Save**.
1. Copy the page URL for the survey. This URL should be the app URL followed by
   `/survey/<Ground-survey-ID>`
1. Send the survey URL to the collaborators.

> Note: When you add a collaborator from the _Share with collaborators_ window,
> they do not automatically receive a notification via email. You need to
> manually send them the survey URL. Also, if the collaborator is prompted to
> create a survey after signing into Ground for the first time, they may need
> to reload the survey URL.

### Distributing the Android app {#distributing}

The easiest way to distribute the Android app is to upload the **gnd-debug.apk**
file generated in [3. Configure and build the Android
app](#configure-and-build-android) to Google Drive and
then [share the
file](https://support.google.com/drive/answer/2494822?co=GENIE.Platform%3DDesktop)
with your survey collaborators. When users open the link on
their Android devices, they will be prompted to download and install the app.

> Note: Depending on the user’s Android settings, they may need to allow apps from
> unknown sources. The location of this setting varies between Android versions.
> In Android 11, this setting must be enabled for the app that distributes the
> unknown app (Google Drive) within **Settings > Apps & notifications > Advanced >
> Special app access > Install unknown apps**.

## Troubleshooting

### The Android build or Gradle sync fails

If the build or Gradle sync fails, first check the **Event Log** and **Build**
panels for errors. Then, make sure you have the following SDK tools installed:

- Android Studio SDK
- NDK (Side by side)
- Android SDK Command-line Tools
- Android Emulator
- Android SDK Platform-Tools

You can view and install SDK tools from **Android Studio > Preferences >
Appearance & Behavior > System Settings > Android SDK > SDK Tools**. 

### Collaborators can’t sign into the survey

Make sure that you have [created a passlist collection](#create-passlist) to
your Firestore instance
and added all of your collaborators. 

You can view and edit your passlist on the _Cloud Firestore_ page of your
Firebase survey by selecting **Firestore Database** within the _Build_ section
of the left nav. The first column on the _Data_ tab contains a list of your
survey’s collections, one of which should be named **passlist**. The passlist
collection should contain separate documents for each user that you want to have
access to the survey, with the user’s Google account (e.g. name@gmail.com) as
the _Document ID_. There are no required fields for documents, but you may want
to create fields to keep track of the user’s organization or who they were added
by. 

### Collaborators can’t access the Ground survey after signing into the web app

If the collaborator is prompted to create a survey after signing into Ground
for the first time, they may need to reload the survey URL:

**https://&lt;your-Firebase-survey-name&gt;.web.app/survey/&lt;Ground-survey-ID&gt;**

### Collaborators can’t access the Ground survey after signing into the Android app

Make sure that you share the Ground survey with collaborators using the web
app. You can also view and modify the users for each survey from the Firestore
console in Firebase. Users for each survey are listed in the _acl_ field of the
survey document within the _surveys_ collection.
