'use strict';

const googleAuth = require('google-auth-library');
const functions = require('firebase-functions');
const fs = require('fs');

const clientSecret = JSON.parse(
  fs.readFileSync(__dirname + '/client-secret.json', 'utf8'));
// TODO: Fail if not found or invalid.
// Auth with: https://us-central1-gnddemo1.cloudfunctions.net/authgoogleapi
const CLIENT_ID = clientSecret['web']['client_id'];
const CLIENT_SECRET = clientSecret['web']['client_secret'];
const DB_TOKEN_PATH = '/api_tokens';
const FUNCTIONS_REDIRECT = clientSecret['web']['redirect_uris'][0];
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
    if (err) {
      res.status(400).send(err);
      return;
    }
    // tokens contains an access_token and an optional refresh_token.
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