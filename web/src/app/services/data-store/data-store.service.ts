/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {getStorage, getDownloadURL, ref} from 'firebase/storage';
import {Injectable} from '@angular/core';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {DocumentData, FieldPath} from '@angular/fire/firestore';
import {FirebaseDataConverter} from 'app/converters/firebase-data-converter';
import {firstValueFrom, Observable, of} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {map} from 'rxjs/operators';
import {User} from 'app/models/user.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Job} from 'app/models/job.model';
import {List, Map} from 'immutable';
import {Submission} from 'app/models/submission/submission.model';
import {Role} from 'app/models/role.model';
import {OfflineBaseMapSource} from 'app/models/offline-base-map-source';
import {LoiDataConverter} from 'app/converters/loi-converter/loi-data-converter';
import {deleteField, serverTimestamp} from 'firebase/firestore';
import {Task} from 'app/models/task/task.model';

const SURVEYS_COLLECTION_NAME = 'surveys';

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  private readonly VALID_ROLES = [
    'owner',
    'data-collector',
    'survey-organizer',
    'viewer',
  ];
  constructor(private db: AngularFirestore) {}

  /**
   * Returns an Observable that loads and emits the survey with the specified
   * uuid.
   *
   * @param id the id of the requested survey.
   */
  loadSurvey$(id: string) {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(id)
      .valueChanges()
      .pipe(
        // Convert object to Survey instance.
        map(data => FirebaseDataConverter.toSurvey(id, data as DocumentData))
      );
  }

  /**
   * Returns an Observable that loads and emits the list of surveys accessible to the specified user.
   *
   */
  loadAccessibleSurvey$(userEmail: string): Observable<List<Survey>> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME, ref =>
        ref.where(new FieldPath('acl', userEmail), 'in', Object.keys(Role))
      )
      .snapshotChanges()
      .pipe(
        map(surveys =>
          List(
            surveys.map(a => {
              const docData = a.payload.doc.data() as DocumentData;
              const id = a.payload.doc.id;
              return FirebaseDataConverter.toSurvey(id, docData);
            })
          )
        )
      );
  }

  /**
   * Updates the survey with new title.
   *
   * @param surveyId the id of the survey.
   * @param newTitle the new title of the survey.
   */
  updateSurveyTitle(surveyId: string, newTitle: string): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set({title: newTitle}, {merge: true});
  }

  /**
   * Updates the survey with new title and new description.
   *
   * @param surveyId the id of the survey.
   * @param newTitle the new title of the survey.
   * @param newDescription the new description of the survey.
   */
  updateSurveyTitleAndDescription(
    surveyId: string,
    newTitle: string,
    newDescription: string
  ): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set({title: newTitle, description: newDescription}, {merge: true});
  }

  addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${job.id}`]: FirebaseDataConverter.jobToJS(job),
      });
  }

  async deleteJob(surveyId: string, jobId: string) {
    await this.deleteAllLocationsOfInterestInJob(surveyId, jobId);
    await this.deleteAllSubmissionsInJob(surveyId, jobId);
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${jobId}`]: deleteField(),
      });
  }

  private async deleteAllSubmissionsInJob(surveyId: string, jobId: string) {
    const submissions = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await firstValueFrom(submissions.get());
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllSubmissionsInLocationOfInterest(
    surveyId: string,
    loiId: string
  ) {
    const submissions = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`,
      ref => ref.where('loiId', '==', loiId)
    );
    const querySnapshot = await firstValueFrom(submissions.get());
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllLocationsOfInterestInJob(
    surveyId: string,
    jobId: string
  ) {
    const loisInJob = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await firstValueFrom(loisInJob.get());
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  async deleteLocationOfInterest(surveyId: string, loiId: string) {
    await this.deleteAllSubmissionsInLocationOfInterest(surveyId, loiId);
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('lois')
      .doc(loiId)
      .delete();
  }

  async deleteSubmission(surveyId: string, submissionId: string) {
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('submissions')
      .doc(submissionId)
      .delete();
  }

  updateSubmission(surveyId: string, submission: Submission) {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`)
      .doc(submission.id)
      .set(FirebaseDataConverter.submissionToJS(submission));
  }

  /**
   * Returns an Observable that loads and emits the LOI with the specified
   * uuid.
   *
   * @param surveyId the id of the survey in which requested LOI is.
   * @param loiId the id of the requested LOI.
   */
  loadLocationOfInterest$(
    surveyId: string,
    loiId: string
  ): Observable<LocationOfInterest> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`)
      .doc(loiId)
      .get()
      .pipe(
        // Fail with error if LOI could not be loaded.
        map(doc => {
          const loi = LoiDataConverter.toLocationOfInterest(
            doc.id,
            doc.data()! as DocumentData
          );
          if (loi instanceof Error) {
            throw loi;
          }

          return loi;
        })
      );
  }

  /**
   * Returns a stream containing the user with the specified id. Remote changes
   * to the user will cause a new value to be emitted.
   *
   * @param uid the unique id used to represent the user in the data store.
   */
  user$(uid: string): Observable<User | undefined> {
    return this.db
      .doc<User>(`users/${uid}`)
      .valueChanges()
      .pipe(map(data => FirebaseDataConverter.toUser(data as DocumentData)));
  }

  lois$({id}: Survey): Observable<List<LocationOfInterest>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${id}/lois`)
      .valueChanges({idField: 'id'})
      .pipe(
        map(array =>
          List(
            array
              .map(obj => {
                const loi = LoiDataConverter.toLocationOfInterest(obj.id, obj);
                if (loi instanceof Error) {
                  throw loi;
                }

                return loi;
              })
              // Filter out LOIs that could not be loaded (i.e., undefined).
              .filter(f => !!f)
          )
        )
      );
  }

  /**
   * Returns an Observable that loads and emits the submissions with the specified
   * uuid.
   *
   * @param id the id of the requested survey (it should have tasks inside).
   * @param loiId the id of the requested loi.
   */
  submissions$(
    survey: Survey,
    loi: LocationOfInterest
  ): Observable<List<Submission>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`, ref =>
        ref.where('loiId', '==', loi.id)
      )
      .valueChanges({idField: 'id'})
      .pipe(
        map(array =>
          List(
            array.map(obj => {
              return FirebaseDataConverter.toSubmission(
                survey.getJob(loi.jobId)!,
                obj.id,
                obj
              );
            })
          )
        )
      );
  }

  // TODO: Define return type here and throughout.
  loadSubmission$(
    survey: Survey,
    loi: LocationOfInterest,
    submissionId: string
  ) {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`)
      .doc(submissionId)
      .get()
      .pipe(
        map(doc => {
          return FirebaseDataConverter.toSubmission(
            survey.getJob(loi.jobId)!,
            doc.id,
            doc.data()! as DocumentData
          );
        })
      );
  }

  tasks$(surveyId: string, jobId: string): Observable<List<Task>> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .get()
      .pipe(
        map(doc => {
          if (!doc.exists) {
            return List<Task>();
          }
          const data = doc.data() as DocumentData;
          const survey = FirebaseDataConverter.toSurvey(surveyId, data);
          const tasks = survey.getJob(jobId)?.tasks;
          if (!tasks || typeof tasks === 'undefined') {
            return List<Task>();
          }
          return tasks.toList()!;
        })
      );
  }

  /**
   * Adds or overwrites the role of the specified user in the survey with the
   * specified id.
   * @param surveyId the id of the survey to be updated.
   * @param email the email of the user whose role is to be updated.
   * @param role the new role of the specified user.
   */
  updateAcl(surveyId: string, acl: Map<string, Role>): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({acl: FirebaseDataConverter.aclToJs(acl)});
  }

  generateId() {
    return this.db.collection('ids').ref.doc().id;
  }

  getServerTimestamp() {
    return serverTimestamp();
  }

  updateLocationOfInterest(
    surveyId: string,
    loi: LocationOfInterest
  ): Promise<void> {
    const loiJs = LoiDataConverter.loiToJS(loi);
    if (loiJs instanceof Error) {
      throw loiJs;
    }

    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('lois')
      .doc(loi.id)
      .set(loiJs);
  }

  /**
   * Creates a new survey in the remote db using the specified title,
   * returning the id of the newly created survey. ACLs are initialized
   * to include the specified user email as survey owner.
   */
  async createSurvey(
    ownerEmail: string,
    title: string,
    description: string,
    offlineBaseMapSources?: OfflineBaseMapSource[]
  ): Promise<string> {
    const surveyId = this.generateId();
    await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(
        FirebaseDataConverter.newSurveyJS(
          ownerEmail,
          title,
          description,
          offlineBaseMapSources
        )
      );
    return Promise.resolve(surveyId);
  }

  getImageDownloadURL(path: string) {
    return getDownloadURL(ref(getStorage(), path));
  }

  addOrUpdateTasks(
    surveyId: string,
    jobId: string,
    tasks: List<Task>
  ): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${jobId}.tasks`]: FirebaseDataConverter.tasksToJs(tasks),
      });
  }
}
