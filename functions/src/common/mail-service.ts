import * as nodemailer from 'nodemailer';
import {Datastore} from './datastore';

type MailConfig = {
  defaultServer?: string;
  servers?: {[key: string]: MailServerConfig};
};

type MailServerConfig = {
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
  private transporter_: any;

  constructor(mailServerConfig: MailServerConfig) {
    const {host, port, username, password, sender} = mailServerConfig;

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
    const mail = await db.fetchMail();
    if (!mail.exists) throw new Error('Unable to find Mail Configuration');
    const mailConfig = mail.data() as MailConfig;
    if (!mailConfig.defaultServer)
      throw new Error('Unable to find Default Server');
    const mailServerConfig =
      mailConfig.servers && mailConfig.servers[mailConfig.defaultServer];
    if (!mailServerConfig)
      throw new Error('Unable to find Mail Server Configuration');
    return mailServerConfig;
  }
}
