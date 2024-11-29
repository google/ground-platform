import * as nodemailer from 'nodemailer';
import {getDatastore} from './context';

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

export const sendMail = async (email: MailServiceEmail) => {
  const db = getDatastore();

  const mailServerConfig = await db.fetchMailServerConfig();

  if (mailServerConfig.exists) {
    const {host, port, username, password, sender} =
      mailServerConfig.data() as MailServerConfig;

    const transporter = nodemailer.createTransport({
      host,
      port,
      auth: {user: username, pass: password},
    });

    const mailDefaults = {sender};

    await new Promise<void>((resolve, reject) => {
      transporter.sendMail(
        {...mailDefaults, ...email},
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
};
