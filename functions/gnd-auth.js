'use strict';

const googleAuth = require('google-auth-library');
const functions = require('firebase-functions');

// Auth with: https://us-central1-gnddemo1.cloudfunctions.net/authgoogleapi
// TODO: Use firebase functions:config:set to configure your googleapi object:
// googleapi.client_id = Google API client ID, 
// googleapi.client_secret = client secret, and 
const CLIENT_ID = functions.config().googleapi.client_id;
const CLIENT_SECRET = functions.config().googleapi.client_secret;
const DB_TOKEN_PATH = '/api_tokens';
// TODO: Deploy inside webapp and update callback URL.
const FUNCTIONS_REDIRECT = `https://us-central1-${process.env.GCLOUD_PROJECT}.cloudfunctions.net/oauthcallback`;
const SCOPES = ['https://www.googleapis.com/auth/spreadsheets'];

class GndAuth {
  constructor(adminDb) {
    this.adminDb_ = adminDb;
    // OAuth token cached locally.
    this.oauthTokens_ = null;
    const auth = new googleAuth();
    this.oauthClient_ = new auth.OAuth2(CLIENT_ID, CLIENT_SECRET, FUNCTIONS_REDIRECT);
  }

  // checks if oauthTokens have been loaded into memory, and if not, retrieves them
  getAuthorizedClient() {
    if (this.oauthTokens_) {
      console.log('Reusing token');
      return Promise.resolve(this.oauthClient_);
    }
    console.log('Fetching token');
    return this.adminDb_.ref(DB_TOKEN_PATH).once('value').then(snapshot => {
      console.log('Got token');
      this.oauthTokens_ = snapshot.val();
      this.oauthClient_.setCredentials(this.oauthTokens_);
      return this.oauthClient_;
    }).catch(err => {
      console.error('Error fetching auth token from db:', err); 
    });
  }
}

// TODO: how do you declare these inside the class?
GndAuth.prototype.oauthcallback = function(req, res) {
  const code = req.query.code;
  this.oauthClient_.getToken(code, (err, tokens) => {
    // Now tokens contains an access_token and an optional refresh_token. Save them.
    if (err) {
      res.status(400).send(err);
      return;
    }
    // TODO: Move token to Firestore instead of Firebase.
    this.adminDb_
      .ref(DB_TOKEN_PATH)
      .set(tokens)
      .then(() =>
        res
          .status(200)
          .send(
            "App successfully configured with new Credentials. " +
              "You can now close this page."
          )
      ).catch(err => {
        res.status(500).send(err);
      });
  });
};

GndAuth.prototype.authgoogleapi = function(req, res) {
  res.redirect(
    this.oauthClient_.generateAuthUrl({
      access_type: "offline",
      scope: SCOPES,
      prompt: "consent"
    })
  );
};

module.exports = GndAuth;