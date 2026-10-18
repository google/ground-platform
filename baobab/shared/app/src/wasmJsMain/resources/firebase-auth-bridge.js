/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Browser bridge to Firebase Authentication for the Ground web clients (`FirebaseWebAuthClient`).
//
// The Firebase project comes from `window.GROUND_FIREBASE_CONFIG`, set by `firebase-config.js`
// (generated from FIREBASE_WEB_CONFIG at build time). Without it, `isConfigured()` is false and the
// apps fall back to their fake sign-in. The Firebase JS SDK is loaded from Google's CDN on first
// use, so pages that never sign in don't download it.
//
// Every entry point reports back through a callback, so Kotlin/Wasm doesn't need Promise interop:
// profiles are JSON strings (`{"displayName", "email", "photoUrl"}`) and errors are strings
// starting with the Firebase error code (for example `auth/popup-closed-by-user: ...`).
window.GroundFirebaseAuthBridge = (function () {
  const SDK_URL = 'https://www.gstatic.com/firebasejs/10.12.2/';
  let ready = null;

  function config() {
    const c = window.GROUND_FIREBASE_CONFIG;
    return c && typeof c === 'object' && c.apiKey ? c : null;
  }

  /** Loads the SDK and initializes the app once; resolves to `{ auth, sdk }`. */
  function load() {
    if (!ready) {
      ready = (async () => {
        const app = await import(SDK_URL + 'firebase-app.js');
        const sdk = await import(SDK_URL + 'firebase-auth.js');
        const auth = sdk.getAuth(app.initializeApp(config()));
        return { auth, sdk };
      })();
    }
    return ready;
  }

  function profile(user) {
    return JSON.stringify({
      displayName: user.displayName || '',
      email: user.email || '',
      photoUrl: user.photoURL || '',
    });
  }

  function describe(error) {
    if (!error) return 'unknown error';
    return (error.code ? error.code + ': ' : '') + (error.message || String(error));
  }

  return {
    isConfigured() {
      return config() !== null;
    },

    /**
     * Calls `onChange(profileJson)` with the signed-in user (or `null`) now and on every change.
     * The first call means the persisted session was restored. `onError(message)` reports a failure
     * to load Firebase.
     */
    observe(onChange, onError) {
      load()
        .then(({ auth, sdk }) =>
          sdk.onAuthStateChanged(auth, (user) => onChange(user ? profile(user) : null))
        )
        .catch((e) => onError(describe(e)));
    },

    /** Signs in with a Google account in a popup; calls `callback(profileJson, error)`. */
    signInWithGoogle(callback) {
      load()
        .then(({ auth, sdk }) => sdk.signInWithPopup(auth, new sdk.GoogleAuthProvider()))
        .then((result) => callback(profile(result.user), null))
        .catch((e) => callback(null, describe(e)));
    },

    /** Signs out; calls `callback(error)`. */
    signOut(callback) {
      load()
        .then(({ auth, sdk }) => sdk.signOut(auth))
        .then(() => callback(null))
        .catch((e) => callback(describe(e)));
    },

    /**
     * Calls `callback(token, error)` with the signed-in user's ID token (refreshed when `force`),
     * or a null token when signed out.
     */
    idToken(force, callback) {
      load()
        .then(({ auth }) => (auth.currentUser ? auth.currentUser.getIdToken(force) : null))
        .then((token) => callback(token, null))
        .catch((e) => callback(null, describe(e)));
    },
  };
})();
