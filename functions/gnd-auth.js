'use strict';

const functions = require('firebase-functions');
const serviceAccount = require('./service-account-secret.json');
const {JWT} = require('google-auth-library');

const API_SCOPES = ['https://www.googleapis.com/auth/spreadsheets'];

class GndAuth {
  constructor() {
    this.jwtClient_ = null;
  }

  getAuthorizedClient() {
    if (!this.jwtClient_) {
      this.jwtClient_ = new JWT({
        'email': serviceAccount['client_email'],
        'key': serviceAccount['private_key'],
        'scopes': API_SCOPES
      });
    }
    return Promise.resolve(this.jwtClient_);
  }
}

module.exports = GndAuth;