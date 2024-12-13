/**
 * Copyright 2024 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as nodemailer from 'nodemailer';
import sanitizeHtml from 'sanitize-html';
import {Datastore} from './datastore';

type MailConfig = {
  server?: MailServerConfig;
};

type MailServerConfig = {
  id: string;
  host: string;
  port: number;
  username: string;
  password: string;
  sender?: string;
};

export interface MailServiceEmail {
  to: string;
  subject: string;
  html: string;
}

/**
 * Service for sending emails.
 */
export class MailService {
  private transporter_: nodemailer.Transporter;
  private sender_: string;

  constructor(mailServerConfig: MailServerConfig) {
    const {host, port, username, password, sender} = mailServerConfig;

    this.sender_ = sender || username;

    this.transporter_ = nodemailer.createTransport({
      host,
      port,
      auth: {user: username, pass: password},
      sender: this.sender_,
    });
  }

  /**
   * Sends an email.
   *
   * @param email - Email object containing recipient, subject, and body.
   */
  async sendMail(email: MailServiceEmail): Promise<void> {
    const {html} = email;

    const safeHtnl = sanitizeHtml(html, {
      allowedTags: ['br', 'a'],
      allowedAttributes: {
        a: ['href'],
      },
    });

    await new Promise<void>((resolve, reject) => {
      this.transporter_.sendMail(
        {from: this.sender_, ...email, html: safeHtnl},
        (error: Error | any, _: any) => {
          if (error) {
            // 501 and 550 are errors from the mail server: email address not found
            if (error.responseCode === 501 || error.responseCode === 550) {
              reject(new Error(error.response));
            } else {
              reject(error);
            }
          } else {
            resolve();
          }
        }
      );
    });
  }

  /**
   * Retrieves the mail server configuration from the database.
   */
  static async getMailServerConfig(
    db: Datastore
  ): Promise<MailServerConfig | undefined> {
    const mailConfig = (await db.fetchMailConfig()) as MailConfig;
    if (!mailConfig) console.error('Unable to find mail configuration');
    const {server: mailServerConfig} = mailConfig;
    if (!mailServerConfig)
      console.error('Mail server config not found in /config/mail/server');
    return mailServerConfig;
  }
}
