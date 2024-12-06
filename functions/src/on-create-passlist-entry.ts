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

import {EventContext} from 'firebase-functions';
import {QueryDocumentSnapshot} from 'firebase-functions/v1/firestore';
import {getMailService} from './common/context';
import {MailServiceEmail} from './common/mail-service';

export async function onCreatePasslistEntryHandler(
  _: QueryDocumentSnapshot,
  context: EventContext
) {
  const entryId = context!.params.entryId;

  const mail = {
    to: entryId,
    subject: 'You now have access to Open Foris Ground',
    html: `
      Dear ${entryId},<br><br>
      We're pleased to inform you that your access request to Open Foris Ground has been approved.<br><br>
      You can now access the website at <a href="https://ground.openforis.org">https://ground.openforis.org</a> and collect data with the mobile app available here: <a href="https://play.google.com/store/apps/details?id=org.openforis.ground">https://play.google.com/store/apps/details?id=org.openforis.ground</a>.<br><br>
      If you have any questions or encounter any issues, please don't hesitate to contact us at <a href="mailto:OpenForis-Ground@fao.org">OpenForis-Ground@fao.org</a>.<br><br>
      Best regards,<br>
      Open Foris Ground Team
    `,
  } as MailServiceEmail;

  const mailService = await getMailService();

  await mailService?.sendMail(mail);
}
