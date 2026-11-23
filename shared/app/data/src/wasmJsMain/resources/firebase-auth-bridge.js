/*
 * Copyright 2026 The Collect One Authors.
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

// Browser bridge to Firebase Authentication for the Collect One web clients (`FirebaseWebAuthClient`).
//
// The Firebase project comes from `window.COLLECT_ONE_FIREBASE_CONFIG`, set by `firebase-config.js`
// (generated from FIREBASE_WEB_CONFIG at build time). Without it, `isConfigured()` is false and the
// apps fall back to their fake sign-in. The Firebase JS SDK is loaded from Google's CDN on first
// use, so pages that never sign in don't download it.
//
// Every entry point reports back through a callback, so Kotlin/Wasm doesn't need Promise interop:
// profiles are JSON strings (`{"displayName", "email", "photoUrl"}`) and errors are strings
// starting with the Firebase error code (for example `auth/popup-closed-by-user: ...`).
window.AppFirebaseAuthBridge = (function () {
  const SDK_URL = 'https://www.gstatic.com/firebasejs/10.12.2/';
  let ready = null;

  function config() {
    const c = window.COLLECT_ONE_FIREBASE_CONFIG;
    return c && typeof c === 'object' && c.apiKey ? c : null;
  }

  /**
   * Loads the SDK and initializes the app once; resolves to `{ auth, sdk }`. A failed load (for
   * example offline) is retried by the next call.
   */
  function load() {
    if (!ready) {
      ready = (async () => {
        const app = await import(SDK_URL + 'firebase-app.js');
        const sdk = await import(SDK_URL + 'firebase-auth.js');
        const firebaseApp = app.getApps().length ? app.getApp() : app.initializeApp(config());
        return { auth: sdk.getAuth(firebaseApp), sdk };
      })().catch((e) => {
        ready = null;
        throw e;
      });
    }
    return ready;
  }

  const AVATAR_CACHE_PREFIX = 'COLLECT_ONE_AVATAR_CACHE:';
  const avatarMemoryCache = new Map();
  const avatarInFlight = new Map();

  function arrayBufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
    }
    return window.btoa(binary);
  }

  function getCachedAvatarBase64(url) {
    if (!url) return null;
    if (avatarMemoryCache.has(url)) return avatarMemoryCache.get(url);
    try {
      const stored = window.localStorage.getItem(AVATAR_CACHE_PREFIX + url);
      if (stored) {
        avatarMemoryCache.set(url, stored);
        return stored;
      }
    } catch (e) {}
    return null;
  }

  function storeCachedAvatarBase64(url, base64) {
    if (!url || !base64) return;
    avatarMemoryCache.set(url, base64);
    try {
      window.localStorage.setItem(AVATAR_CACHE_PREFIX + url, base64);
    } catch (e) {}
  }

  function fetchAvatarBase64Promise(url) {
    if (!url) return Promise.resolve(null);
    const cached = getCachedAvatarBase64(url);
    if (cached) return Promise.resolve(cached);
    if (avatarInFlight.has(url)) return avatarInFlight.get(url);
    const promise = fetch(url, {
      mode: 'cors',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    })
      .then((resp) => {
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        return resp.arrayBuffer();
      })
      .then((buf) => {
        if (!buf || buf.byteLength === 0) return null;
        const base64 = arrayBufferToBase64(buf);
        storeCachedAvatarBase64(url, base64);
        return base64;
      })
      .catch(() => null)
      .finally(() => {
        avatarInFlight.delete(url);
      });
    avatarInFlight.set(url, promise);
    return promise;
  }

  function profile(user) {
    const photoUrl = user.photoURL || '';
    if (photoUrl) {
      fetchAvatarBase64Promise(photoUrl);
    }
    return JSON.stringify({
      displayName: user.displayName || '',
      email: user.email || '',
      photoUrl: photoUrl,
    });
  }

  function describe(error) {
    if (!error) return 'unknown error';
    return (error.code ? error.code + ': ' : '') + (error.message || String(error));
  }

  function passlistPatterns() {
    const raw = window.COLLECT_ONE_PASSLIST;
    if (Array.isArray(raw)) {
      return raw.map((s) => String(s).trim().toLowerCase()).filter(Boolean);
    }
    if (typeof raw === 'string' && raw.trim()) {
      return raw
        .split(/[,;\n]+/)
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean);
    }
    return [];
  }

  function matchesPasslistPattern(email, domain, pattern) {
    if (pattern.startsWith('*@*.')) {
      const suffix = pattern.slice(4);
      return suffix.length > 0 && (domain === suffix || domain.endsWith('.' + suffix));
    }
    if (pattern.startsWith('*.')) {
      const suffix = pattern.slice(2);
      return suffix.length > 0 && (domain === suffix || domain.endsWith('.' + suffix));
    }
    if (pattern.startsWith('*@')) {
      const targetDomain = pattern.slice(2);
      return targetDomain.length > 0 && domain === targetDomain;
    }
    if (pattern.startsWith('@')) {
      const targetDomain = pattern.slice(1);
      return targetDomain.length > 0 && domain === targetDomain;
    }
    return email === pattern;
  }

  function isEmailPasslisted(email) {
    const patterns = passlistPatterns();
    if (patterns.length === 0) return true;
    const normalized = String(email || '').trim().toLowerCase();
    const atIndex = normalized.lastIndexOf('@');
    if (atIndex <= 0 || atIndex === normalized.length - 1) return false;
    const domain = normalized.slice(atIndex + 1);
    return patterns.some((pattern) => matchesPasslistPattern(normalized, domain, pattern));
  }

  const DEV_ROLE_KEY = 'COLLECT_ONE_DEV_ROLE';

  return {
    isConfigured() {
      return config() !== null;
    },

    /** Whether this build offers development sign-in (`window.COLLECT_ONE_DEV_SIGN_IN`). */
    isDevSignIn() {
      return window.COLLECT_ONE_DEV_SIGN_IN === true;
    },

    /** The development role (`admin` or `user`) remembered on this device, or `admin`. */
    devRole() {
      try {
        return window.localStorage.getItem(DEV_ROLE_KEY) === 'user' ? 'user' : 'admin';
      } catch (e) {
        return 'admin';
      }
    },

    setDevRole(role) {
      try {
        window.localStorage.setItem(DEV_ROLE_KEY, role);
      } catch (e) {}
    },

    /** Returns the base64-encoded image bytes cached in localStorage for `url`, or `null`. */
    cachedAvatarBase64(url) {
      return getCachedAvatarBase64(url);
    },

    /** Fetches `url`, caches its base64 bytes in localStorage, and calls `callback(base64)`. */
    fetchAvatarBase64(url, callback) {
      fetchAvatarBase64Promise(url).then((base64) => callback(base64 || null));
    },

    /**
     * Calls `onChange(profileJson)` with the signed-in user (or `null`) now and on every change.
     * The first call means the persisted session was restored. `onError(message)` reports a failure
     * to load Firebase.
     */
    observe(onChange, onError) {
      load()
        .then(({ auth, sdk }) =>
          sdk.onAuthStateChanged(auth, (user) => {
            if (user && !isEmailPasslisted(user.email)) {
              sdk.signOut(auth).catch(() => {});
              onChange(null);
              return;
            }
            onChange(user ? profile(user) : null);
          })
        )
        .catch((e) => onError(describe(e)));
    },

    /** Signs in with a Google account in a popup; calls `callback(profileJson, error)`. */
    signInWithGoogle(callback) {
      load()
        .then(async ({ auth, sdk }) => {
          const provider = new sdk.GoogleAuthProvider();
          provider.setCustomParameters({ prompt: 'select_account' });
          const result = await sdk.signInWithPopup(auth, provider);
          if (!isEmailPasslisted(result.user && result.user.email)) {
            const rejectedEmail = (result.user && result.user.email) || 'unknown account';
            await sdk.signOut(auth);
            const err = new Error('Account ' + rejectedEmail + ' is not on the passlist.');
            err.code = 'auth/not-passlisted';
            throw err;
          }
          return result;
        })
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
