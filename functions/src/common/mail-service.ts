import * as nodemailer from 'nodemailer';
import {Datastore} from './datastore';

type MailServerConfig = {
  host: string;
  port: number;
  username: string;
  password: string;
  sender?: string;
};

export interface MailServiceEmail {
  from?: string;
  to: string;
  subject: string;
  html: string;
}

export class MailService {
  private transporter_: any;

  constructor(config: MailServerConfig) {
    const {host, port, username, password, sender} = config;

    this.transporter_ = nodemailer.createTransport({
      host,
      port,
      auth: {user: username, pass: password},
      sender: sender || username,
    });
  }

  async sendMail(email: MailServiceEmail) {
    await new Promise<void>((resolve, reject) => {
      this.transporter_.sendMail(email, (error: Error | any, _info: any) => {
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
      });
    });
  }

  static async gerMailServerConfig(db: Datastore): Promise<MailServerConfig> {
    const mailServerConfig = await db.fetchMailServerConfig();

    if (mailServerConfig.exists) {
      return mailServerConfig.data() as MailServerConfig;
    } else {
      throw new Error('Unable to find Mail Server Configuration');
    }
  }
}
