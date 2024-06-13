/**
 * Copyright 2019 The Ground Authors.
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

import {Injectable} from '@angular/core';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {
  DocumentData,
  FieldPath,
  deleteField,
  serverTimestamp,
} from '@angular/fire/firestore';
import {getDownloadURL, getStorage, ref} from 'firebase/storage';
import {List, Map} from 'immutable';
import {Observable, firstValueFrom} from 'rxjs';
import {map} from 'rxjs/operators';

import {FirebaseDataConverter} from 'app/converters/firebase-data-converter';
import {LoiDataConverter} from 'app/converters/loi-converter/loi-data-converter';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Role} from 'app/models/role.model';
import {Submission} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';

const SURVEYS_COLLECTION_NAME = 'surveys';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type JsonBlob = {[field: string]: any};

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
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
   * Returns an Observable that loads and emits the job with the specified
   * uuid and survey uuid.
   *
   * @param jobId the id of the requested job.
   * @param surveyId the id of the requested survey.
   */
  loadJob$(jobId: string, surveyId: string) {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .valueChanges()
      .pipe(
        // Convert object to Survey instance.
        map(data => {
          const job = FirebaseDataConverter.toSurvey(
            surveyId,
            data as DocumentData
          ).getJob(jobId);

          if (!job) {
            throw Error(`Job $jobId not found`);
          }

          return job;
        })
      );
  }

  /**
   * Returns the raw survey object from the db. Used for debugging only.
   */
  async loadRawSurvey(id: string) {
    return (
      await firstValueFrom(
        this.db.collection(SURVEYS_COLLECTION_NAME).doc(id).get()
      )
    ).data();
  }

  /**
   * Updates the raw survey object in the db. Used for debugging only.
   */
  async saveRawSurvey(id: string, data: JsonBlob) {
    await this.db.collection(SURVEYS_COLLECTION_NAME).doc(id).set(data);
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
   * Updates the survey with new title, description and jobs, and handles deletion of associated
   * LOIs and submissions for the jobs to be deleted. Note that LOI/submission deletion is
   * asynchronous and might leave unreferenced data temporarily. A scheduled job should delete
   * these unreferenced lois and submissions(TODO(#1532)).
   *
   * @param survey The updated Survey object containing the modified survey data.
   * @param jobIdsToDelete List of job ids to delete. This is used to delete all the lois and
   *  submissions that are related to the jobs to be deleted.
   */

  updateSurvey(survey: Survey, jobIdsToDelete: List<string>): Promise<void> {
    const {title, description, id} = survey;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const surveyJS: {[key: string]: any} = {
      title: title,
      description: description,
    };
    survey.jobs.forEach(
      job => (surveyJS[`jobs.${job.id}`] = FirebaseDataConverter.jobToJS(job))
    );
    jobIdsToDelete.forEach(jobId => {
      this.deleteAllLocationsOfInterestInJob(id, jobId);
      this.deleteAllSubmissionsInJob(id, jobId);
      surveyJS[`jobs.${jobId}`] = deleteField();
    });

    return this.db.firestore
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(survey.id)
      .update(surveyJS);
  }

  /**
   * Updates the survey with new name.
   *
   * @param surveyId the id of the survey.
   * @param newName the new name of the survey.
   */
  updateSurveyTitle(surveyId: string, newName: string): Promise<void> {
    console.log('updateSurveyTitle');
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(FirebaseDataConverter.partialSurveyToJS(newName), {
        merge: true,
      });
  }

  /**
   * Updates the survey with new name and new description.
   *
   * @param surveyId the id of the survey.
   * @param newName the new name of the survey.
   * @param newDescription the new description of the survey.
   */
  updateSurveyTitleAndDescription(
    surveyId: string,
    newName: string,
    newDescription: string
  ): Promise<void> {
    console.log('updateSurveyTitleAndDescription');
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(FirebaseDataConverter.partialSurveyToJS(newName, newDescription), {
        merge: true,
      });
  }

  addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${job.id}`]: FirebaseDataConverter.jobToJS(job),
      });
  }

  async deleteSurvey(survey: Survey) {
    const {id: surveyId} = survey;

    await Promise.all(survey.jobs.map(job => this.deleteJob(surveyId, job.id)));

    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .delete();
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
              .map(obj => LoiDataConverter.toLocationOfInterest(obj.id, obj))
              .filter(DataStoreService.filterAndLogError<LocationOfInterest>)
              .map(loi => loi as LocationOfInterest)
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
            array
              .map(obj =>
                FirebaseDataConverter.toSubmission(
                  survey.getJob(loi.jobId)!,
                  obj.id,
                  obj
                )
              )
              .filter(DataStoreService.filterAndLogError<Submission>)
              .map(submission => submission as Submission)
          )
        )
      );
  }

  // TODO: Define return type here and throughout.
  loadSubmission$(
    survey: Survey,
    loi: LocationOfInterest,
    submissionId: string
  ): Observable<Submission | Error> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`)
      .doc(submissionId)
      .get()
      .pipe(
        map(doc =>
          FirebaseDataConverter.toSubmission(
            survey.getJob(loi.jobId)!,
            doc.id,
            doc.data()! as DocumentData
          )
        )
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
   * to include the specified user email as survey organizer.
   */
  async createSurvey(
    ownerEmail: string,
    name: string,
    description: string
  ): Promise<string> {
    const surveyId = this.generateId();
    const acl = Map<string, Role>({[ownerEmail]: Role.SURVEY_ORGANIZER});
    await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(FirebaseDataConverter.partialSurveyToJS(name, description, acl));
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
        [`jobs.${jobId}.tasks`]: FirebaseDataConverter.tasksToJS(
          this.convertTasksListToMap(tasks)
        ),
      });
  }

  /**
   * Converts list of tasks to map.
   */
  convertTasksListToMap(tasks: List<Task>): Map<string, Task> {
    let tasksMap = Map<string, Task>();
    tasks.forEach((task: Task, index: number) => {
      const jobFieldId = tasks && tasks.get(index)?.id;
      const taskId = jobFieldId ? jobFieldId : this.generateId();
      tasksMap = tasksMap.set(taskId, task);
    });
    return tasksMap;
  }

  public static filterAndLogError<T>(entityOrError: T | Error) {
    if (entityOrError instanceof Error) {
      console.error(entityOrError);
      return false;
    }
    return true;
  }
}
