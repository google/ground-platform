'use strict';

const googleAuth = require('google-auth-library');
const functions = require('firebase-functions');
const fs = require('fs');
const url = require('url');

const CLIENT_SECRET_JSON_PATH = __dirname + '/client-secret.json';
const DB_TOKEN_PATH = '/api_tokens';
const SCOPES = ['https://www.googleapis.com/auth/spreadsheets'];

class GndAuth {
  constructor(adminDb) {
    this.adminDb_ = adminDb;
    // OAuth token cached locally.
    this.oauthTokens_ = null;
    this.oauthClient_ =  null;
  }

  getOAuthClient_() {
    if (!this.oauthClient_) {
      const cfg = JSON.parse(
        fs.readFileSync(CLIENT_SECRET_JSON_PATH, 'utf8'))['web'];
      const auth = new googleAuth();
      this.oauthClient_ =
        new auth.OAuth2(cfg['client_id'], cfg['client_secret'],
          cfg['redirect_uris'][0]);
    }
    return this.oauthClient_;
  }

  // checks if oauthTokens have been loaded into memory, and if not, retrieves them
  getAuthorizedClient() {
    if (this.oauthTokens_) {
      console.log('Reusing token');
      return Promise.resolve(this.getOAuthClient_());
    }
    console.log('Fetching token');
    return this.adminDb_.ref(DB_TOKEN_PATH).once('value').then(snapshot => {
      console.log('Got token');
      this.oauthTokens_ = snapshot.val();
      this.getOAuthClient_().setCredentials(this.oauthTokens_);
      return this.getOAuthClient_();
    }).catch(err => {
      console.error('Error fetching auth token from db:', err); 
    });
  }
}

// TODO: how do you declare these inside the class?
GndAuth.prototype.oauthcallback = function(req, res) {
  const code = req.query.code;
  this.getOAuthClient_().getToken(code, (err, tokens) => {
    if (err) {
      console.error(err);
      res.status(400).json(err);
      return;
    }
    var parts = url.parse(req.url, true);
    var query = parts.query;    
    console.log(JSON.stringify(query));
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
    this.getOAuthClient_().generateAuthUrl({
      access_type: "offline",
      scope: SCOPES,
      prompt: "consent",
      state: "foobar"
    })
  );
};

module.exports = GndAuth;