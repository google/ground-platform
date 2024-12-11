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
import {Datastore} from './datastore';

type MailConfig = {
  defaultServer?: string;
  servers?: MailServerConfig[];
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

  async sendMail(email: MailServiceEmail) {
    await new Promise<void>((resolve, reject) => {
      this.transporter_.sendMail(
        {from: this.sender_, ...email},
        (error: Error | any, _info: any) => {
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

  static async gerMailServerConfig(db: Datastore): Promise<MailServerConfig> {
    const mail = await db.fetchMail();
    if (!mail.exists) throw new Error('Unable to find Mail Configuration');
    const mailConfig = mail.data() as MailConfig;
    if (!mailConfig.defaultServer)
      throw new Error('Unable to find Default Server');
    const mailServerConfig = mailConfig.servers?.find(
      ({id}) => id === mailConfig.defaultServer
    );
    if (!mailServerConfig)
      throw new Error('Unable to find Mail Server Configuration');
    return mailServerConfig;
  }
}
