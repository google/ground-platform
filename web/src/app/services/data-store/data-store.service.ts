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
import {
  AngularFirestore,
  CollectionReference,
} from '@angular/fire/compat/firestore';
import {
  DocumentData,
  FieldPath,
  serverTimestamp,
} from '@angular/fire/firestore';
import {registry} from '@ground/lib/dist/message-registry';
import {GroundProtos} from '@ground/proto';
import {getDownloadURL, getStorage, ref} from 'firebase/storage';
import {List, Map} from 'immutable';
import {Observable, combineLatest, firstValueFrom} from 'rxjs';
import {map} from 'rxjs/operators';

import {FirebaseDataConverter} from 'app/converters/firebase-data-converter';
import {loiDocToModel} from 'app/converters/loi-data-converter';
import {
  jobToDocument,
  surveyToDocument,
} from 'app/converters/proto-model-converter';
import {submissionDocToModel} from 'app/converters/submission-data-converter';
import {
  jobDocsToModel,
  surveyDocToModel,
} from 'app/converters/survey-data-converter';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Role} from 'app/models/role.model';
import {Submission} from 'app/models/submission/submission.model';
import {
  DataSharingType,
  Survey,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const s = registry.getFieldIds(Pb.Survey);
const sb = registry.getFieldIds(Pb.Submission);

const Source = Pb.LocationOfInterest.Source;
const AclRole = Pb.Role;

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
  loadSurvey$(id: string): Observable<Survey> {
    return combineLatest([
      this.db.collection(SURVEYS_COLLECTION_NAME).doc(id).valueChanges(),
      this.loadJobs$(id),
    ]).pipe(
      map(surveyAndJobs => {
        const [s, jobs] = surveyAndJobs;

        const survey = surveyDocToModel(id, s as DocumentData, jobs);

        if (survey instanceof Error) throw survey;

        return survey;
      })
    );
  }

  /**
   * Returns an Observable that loads and emits all the jobs
   * based on provided parameters.
   *
   * @param id the id of the requested survey.
   */
  loadJobs$(id: string): Observable<List<Job>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${id}/jobs`)
      .valueChanges()
      .pipe(map(data => jobDocsToModel(data as DocumentData[])));
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
   * Returns an Observable that loads and emits the list of surveys accessible
   * to the specified user.
   *
   */
  loadAccessibleSurveys$(
    userEmail: string,
    userId: string
  ): Observable<List<Survey>> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME, ref =>
        ref.where(new FieldPath(s.acl, userEmail), 'in', [
          AclRole.VIEWER,
          AclRole.DATA_COLLECTOR,
          AclRole.SURVEY_ORGANIZER,
        ])
      )
      .snapshotChanges()
      .pipe(
        map(surveys =>
          List(
            surveys
              .map(a => {
                const docData = a.payload.doc.data() as DocumentData;
                const id = a.payload.doc.id;
                return surveyDocToModel(id, docData);
              })
              .filter(DataStoreService.filterAndLogError<Survey>)
              .filter(
                survey =>
                  survey instanceof Survey &&
                  (survey.generalAccess !== SurveyGeneralAccess.UNLISTED ||
                    (survey.generalAccess === SurveyGeneralAccess.UNLISTED &&
                      survey.ownerId === userId))
              )
              .map(survey => survey as Survey)
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
  async updateSurvey(
    survey: Survey,
    jobIdsToDelete?: List<string>
  ): Promise<void> {
    const {id: surveyId, jobs} = survey;

    jobIdsToDelete?.forEach(jobId => this.deleteJob(surveyId, jobId));

    await Promise.all(
      jobs
        .filter(({id}) => !jobIdsToDelete?.includes(id))
        .map(job => this.addOrUpdateJob(surveyId, job))
    );

    await this.db.firestore
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update(surveyToDocument(surveyId, survey));
  }

  /**
   * Updates the survey with new name.
   *
   * @param surveyId the id of the survey.
   * @param name the new name of the survey.
   */
  updateSurveyTitle(surveyId: string, name: string): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(surveyToDocument(surveyId, {title: name}), {merge: true});
  }

  /**
   * Updates the survey with new name and new description.
   *
   * @param surveyId the id of the survey.
   * @param name the new name of the survey.
   * @param description the new description of the survey.
   */
  updateSurveyTitleAndDescription(
    surveyId: string,
    name: string,
    description: string
  ): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(surveyToDocument(surveyId, {title: name, description}), {
        merge: true,
      });
  }

  addOrUpdateSurvey(survey: Survey): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(survey.id)
      .set(surveyToDocument(survey.id, survey));
  }

  addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/jobs`)
      .doc(job.id)
      .set(jobToDocument(job));
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
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/jobs`)
      .doc(jobId)
      .delete();
  }

  private async deleteAllSubmissionsInJob(surveyId: string, jobId: string) {
    const submissions = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`,
      ref => ref.where(sb.jobId, '==', jobId)
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
      ref => ref.where(sb.loiId, '==', loiId)
    );
    const querySnapshot = await firstValueFrom(submissions.get());
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllLocationsOfInterestInJob(
    surveyId: string,
    jobId: string
  ) {
    const lois = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`,
      ref => ref.where(l.jobId, '==', jobId)
    );
    const querySnapshot = await firstValueFrom(lois.get());
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
      .pipe(
        map(data => FirebaseDataConverter.toUser(data as DocumentData, uid))
      );
  }

  private toLocationsOfInterest(
    loiIds: {id: string}[]
  ): List<LocationOfInterest> {
    return List(
      loiIds
        .map(obj => loiDocToModel(obj.id, obj))
        .filter(DataStoreService.filterAndLogError<LocationOfInterest>)
        .map(loi => loi as LocationOfInterest)
    );
  }

  /**
   * Returns an Observable that loads and emits all the locations of interest
   * based on provided parameters.
   *
   * @param survey the survey instance.
   * @param userEmail the email of the user to filter the results.
   * @param canManageSurvey a flag indicating whether the user has survey organizer or owner level permissions of the survey.
   */
  getAccessibleLois$(
    {id: surveyId}: Survey,
    userId: string,
    canManageSurvey: boolean
  ): Observable<List<LocationOfInterest>> {
    if (canManageSurvey) {
      return this.db
        .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`)
        .valueChanges({idField: 'id'})
        .pipe(map(this.toLocationsOfInterest));
    }

    const importedLois = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`,
      ref => ref.where(l.source, '==', Source.IMPORTED)
    );

    const fieldDataLois = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`,
      ref =>
        ref
          .where(l.source, '==', Source.FIELD_DATA)
          .where(l.ownerId, '==', userId)
    );

    return combineLatest([
      importedLois.valueChanges({idField: 'id'}),
      fieldDataLois.valueChanges({idField: 'id'}),
    ]).pipe(
      map(([predefinedLois, fieldDataLois]) =>
        this.toLocationsOfInterest(predefinedLois.concat(fieldDataLois))
      )
    );
  }

  /**
   * Returns an Observable that loads and emits all the submissions
   * based on provided parameters.
   *
   * @param survey the survey instance.
   * @param loi the loi instance.
   * @param userEmail the email of the user to filter the results.
   * @param canManageSurvey a flag indicating whether the user has survey organizer or owner level permissions of the survey.
   */
  getAccessibleSubmissions$(
    survey: Survey,
    loi: LocationOfInterest,
    userId: string,
    canManageSurvey: boolean
  ): Observable<List<Submission>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`, ref =>
        this.canViewSubmissions(ref, loi.id, userId, canManageSurvey)
      )
      .valueChanges({idField: 'id'})
      .pipe(
        map(array =>
          List(
            array
              .map(obj =>
                submissionDocToModel(survey.getJob(loi.jobId)!, obj.id, obj)
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
          submissionDocToModel(
            survey.getJob(loi.jobId)!,
            doc.id,
            doc.data()! as DocumentData
          )
        )
      );
  }

  tasks$(surveyId: string, jobId: string): Observable<List<Task>> {
    return this.loadJobs$(surveyId).pipe(
      map(jobs => {
        const job = jobs.find(job => job.id === jobId);

        return job?.tasks?.toList() ?? List<Task>();
      })
    );
  }

  generateId() {
    return this.db.collection('ids').ref.doc().id;
  }

  getServerTimestamp() {
    return serverTimestamp();
  }

  /**
   * Creates a new survey in the remote db using the specified title,
   * returning the id of the newly created survey. ACLs are initialized
   * to include the specified user email as survey organizer.
   */
  async createSurvey(
    name: string,
    description: string,
    user: User
  ): Promise<string> {
    const surveyId = this.generateId();
    const {email: ownerEmail, id: ownerId} = user;
    const acl = Map<string, Role>({[ownerEmail]: Role.SURVEY_ORGANIZER});
    await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(
        surveyToDocument(surveyId, {
          title: name,
          description,
          acl,
          ownerId,
          state: SurveyState.DRAFT,
        })
      );
    return Promise.resolve(surveyId);
  }

  getImageDownloadURL(path: string) {
    return getDownloadURL(ref(getStorage(), path));
  }

  async getTermsOfService(): Promise<string> {
    const tos = await this.db.collection('config').doc('tos').ref.get();
    return tos.get('text');
  }

  async getAccessDeniedMessage(): Promise<{message?: string; link?: string}> {
    const accessDeniedMessage = await this.db
      .collection('config')
      .doc('accessDenied')
      .ref.get();
    return {
      message: accessDeniedMessage.get('message'),
      link: accessDeniedMessage.get('link'),
    };
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

  /**
   * Creates a new Query object to filter submissions based on
   * LOI and user IDs. User ID is ignored if user can manage this
   * survey (i.e., they can view all submissions by default).
   */
  private canViewSubmissions(
    ref: CollectionReference,
    loiId: string,
    userId: string,
    canManageSurvey: boolean
  ) {
    const query = ref.where(sb.loiId, '==', loiId);

    return canManageSurvey ? query : query.where(sb.ownerId, '==', userId);
  }
}
