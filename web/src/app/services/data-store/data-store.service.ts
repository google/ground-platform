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

import { Injectable } from '@angular/core';
import {
  Firestore,
  collection,
  doc,
  docData,
  collectionData,
  query,
  where,
  orderBy,
  limit,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  runTransaction,
  writeBatch,
  serverTimestamp,
  getDoc,
  getDocs,
  DocumentReference,
  CollectionReference,
  FieldPath,
  Timestamp,
  DocumentData,
  QueryDocumentSnapshot,
  Transaction,
} from '@angular/fire/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { getDownloadURL, getStorage, ref } from 'firebase/storage';
import { List, Map, OrderedMap } from 'immutable';
import { Observable, combineLatest, firstValueFrom, from } from 'rxjs';
import { map } from 'rxjs/operators';

import { FirebaseDataConverter } from 'app/converters/firebase-data-converter';
import { loiDocToModel } from 'app/converters/loi-data-converter';
import {
  jobToDocument,
  surveyToDocument,
} from 'app/converters/proto-model-converter';
import { submissionDocToModel } from 'app/converters/submission-data-converter';
import {
  jobDocToModel,
  jobDocsToModel,
  surveyDocToModel,
} from 'app/converters/survey-data-converter';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Role } from 'app/models/role.model';
import { Submission } from 'app/models/submission/submission.model';
import {
  Survey,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { User } from 'app/models/user.model';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const s = registry.getFieldIds(Pb.Survey);
const sb = registry.getFieldIds(Pb.Submission);

const Source = Pb.LocationOfInterest.Source;
const AclRole = Pb.Role;
const GeneralAccess = Pb.Survey.GeneralAccess;

const SURVEYS_COLLECTION_NAME = 'surveys';
const JOBS_COLLECTION_NAME = 'jobs';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type JsonBlob = { [field: string]: any };

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  constructor(private db: Firestore) {}

  /**
   * Returns an Observable that loads and emits the survey with the specified
   * uuid.
   *
   * @param id the id of the requested survey.
   */
  loadSurvey$(id: string): Observable<Survey> {
    return combineLatest([
      docData(doc(this.db, SURVEYS_COLLECTION_NAME, id)),
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
    return collectionData(
      collection(this.db, `${SURVEYS_COLLECTION_NAME}/${id}/jobs`)
    ).pipe(map(data => jobDocsToModel(data as DocumentData[])));
  }

  /**
   * Returns the raw survey object from the db. Used for debugging only.
   */
  async loadRawSurvey(id: string) {
    const d = await getDoc(doc(this.db, SURVEYS_COLLECTION_NAME, id));
    return d.data();
  }

  /**
   * Updates the raw survey object in the db. Used for debugging only.
   */
  async saveRawSurvey(id: string, data: JsonBlob) {
    await setDoc(doc(this.db, SURVEYS_COLLECTION_NAME, id), data);
  }



  /**
   * Returns an Observable that loads and emits the list of surveys accessible
   * to the specified user.
   */
  loadAccessibleSurveys$(userEmail: string): Observable<List<Survey>> {
    const surveysRef = collection(this.db, SURVEYS_COLLECTION_NAME);
    const accessibleRestrictedSurveys$ = collectionData(
      query(
        surveysRef,
        where(new FieldPath(s.acl, userEmail), 'in', [
          AclRole.VIEWER,
          AclRole.DATA_COLLECTOR,
          AclRole.SURVEY_ORGANIZER,
        ])
      ),
      { idField: 'id' }
    ).pipe(
      map(surveys =>
        surveys
          .map(s => surveyDocToModel(s['id'], s as DocumentData))
          .filter(DataStoreService.filterAndLogError<Survey>)
          .map(survey => survey as Survey)
      ),
      map(surveys =>
        surveys.filter(
          survey => survey.generalAccess === SurveyGeneralAccess.RESTRICTED
        )
      )
    );

    const accessibleUnlistedSurveys$ = collectionData(
      query(
        surveysRef,
        where(s.generalAccess, '==', GeneralAccess.UNLISTED),
        where(new FieldPath(s.acl, userEmail), '==', AclRole.SURVEY_ORGANIZER)
      ),
      { idField: 'id' }
    ).pipe(
      map(surveys =>
        surveys
          .map(s => surveyDocToModel(s['id'], s as DocumentData))
          .filter(DataStoreService.filterAndLogError<Survey>)
          .map(survey => survey as Survey)
      )
    );

    const publicSurveys$ = collectionData(
      query(surveysRef, where(s.generalAccess, '==', GeneralAccess.PUBLIC)),
      { idField: 'id' }
    ).pipe(
      map(surveys =>
        surveys
          .map(s => surveyDocToModel(s['id'], s as DocumentData))
          .filter(DataStoreService.filterAndLogError<Survey>)
          .map(survey => survey as Survey)
      )
    );

    return combineLatest([
      accessibleRestrictedSurveys$,
      accessibleUnlistedSurveys$,
      publicSurveys$,
    ]).pipe(
      map(([restricted, unlisted, publicSurveys]) =>
        List([...restricted, ...unlisted, ...publicSurveys])
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
    const { id: surveyId, jobs } = survey;

    await runTransaction(this.db, async transaction => {
      if (jobIdsToDelete) {
        for (const jobId of jobIdsToDelete) {
          await this._deleteJobAndRelatedData(transaction, surveyId, jobId);
        }
      }

      const jobsToUpdate = jobs.filter(
        ({ id }) => !jobIdsToDelete?.includes(id)
      );
      for (const job of jobsToUpdate.values()) {
        await this._addOrUpdateJobInTransaction(transaction, surveyId, job);
      }

      const surveyRef = doc(this.db, SURVEYS_COLLECTION_NAME, surveyId);
      transaction.update(surveyRef, surveyToDocument(surveyId, survey));
    });
  }

  /**
   * Updates the survey with new name.
   *
   * @param surveyId The ID of the survey to update.
   * @param name The new name of the survey.
   */
  updateSurveyTitle(surveyId: string, name: string): Promise<void> {
    return setDoc(
      doc(this.db, SURVEYS_COLLECTION_NAME, surveyId),
      surveyToDocument(surveyId, { title: name }),
      { merge: true }
    );
  }

  /**
   * Updates the survey with new name and new description.
   *
   * @param surveyId The ID of the survey to update.
   * @param name The new name of the survey.
   * @param description The new description of the survey.
   */
  updateSurveyTitleAndDescription(
    surveyId: string,
    name: string,
    description: string
  ): Promise<void> {
    return setDoc(
      doc(this.db, SURVEYS_COLLECTION_NAME, surveyId),
      surveyToDocument(surveyId, { title: name, description }),
      { merge: true }
    );
  }

  addOrUpdateSurvey(survey: Survey): Promise<void> {
    return setDoc(
      doc(this.db, SURVEYS_COLLECTION_NAME, survey.id),
      surveyToDocument(survey.id, survey)
    );
  }

  addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    return setDoc(
      doc(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/jobs`, job.id),
      jobToDocument(job)
    );
  }

  /**
   * @private
   * Adds or updates a specific job within a Firestore transaction.
   *
   * @param transaction The Firestore transaction to perform the operation within.
   * @param surveyId The ID of the survey the job belongs to.
   * @param job The Job object to add or update.
   */
  private async _addOrUpdateJobInTransaction(
    transaction: Transaction,
    surveyId: string,
    job: Job
  ): Promise<void> {
    const jobRef = doc(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/jobs`, job.id);
    transaction.set(jobRef, jobToDocument(job));
  }

  /**
   * @private
   * Deletes a specific job and all its associated Locations of Interest (LOIs)
   * and submissions within a Firestore transaction.
   *
   * @param transaction The Firestore transaction to perform the deletions within.
   * @param surveyId The ID of the survey the job belongs to.
   * @param jobId The ID of the job to delete.
   */
  private async _deleteJobAndRelatedData(
    transaction: Transaction,
    surveyId: string,
    jobId: string
  ): Promise<void> {
    const loisQuery = query(
      collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`),
      where(l.jobId, '==', jobId)
    );
    const loisSnapshot = await getDocs(loisQuery);
    loisSnapshot.forEach(doc => {
      transaction.delete(doc.ref);
    });

    const submissionsQuery = query(
      collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`),
      where(sb.jobId, '==', jobId)
    );
    const submissionsSnapshot = await getDocs(submissionsQuery);
    submissionsSnapshot.forEach(doc => {
      transaction.delete(doc.ref);
    });

    const jobRef = doc(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/jobs`, jobId);
    transaction.delete(jobRef);
  }

  async deleteSurvey(survey: Survey): Promise<void> {
    const { id: surveyId, jobs } = survey;

    await runTransaction(this.db, async transaction => {
      for (const { id: jobId } of jobs.values()) {
        await this._deleteJobAndRelatedData(transaction, surveyId, jobId);
      }

      const surveyRef = doc(this.db, SURVEYS_COLLECTION_NAME, surveyId);
      transaction.delete(surveyRef);
    });
  }

  private async deleteSubmissionsByLoiId(
    surveyId: string,
    loiId: string
  ): Promise<void> {
    const submissions = query(
      collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`),
      where(sb.loiId, '==', loiId)
    );
    const querySnapshot = await getDocs(submissions);
    await Promise.all(querySnapshot.docs.map(doc => deleteDoc(doc.ref)));
  }

  async deleteLocationOfInterest(
    surveyId: string,
    loiId: string
  ): Promise<void> {
    await this.deleteSubmissionsByLoiId(surveyId, loiId);

    return await deleteDoc(doc(this.db, SURVEYS_COLLECTION_NAME, surveyId, 'lois', loiId));
  }

  async deleteSubmission(
    surveyId: string,
    submissionId: string
  ): Promise<void> {
    return await deleteDoc(
      doc(
        this.db,
        SURVEYS_COLLECTION_NAME,
        surveyId,
        'submissions',
        submissionId
      )
    );
  }

  updateSubmission(surveyId: string, submission: Submission) {
    return setDoc(
      doc(
        this.db,
        `${SURVEYS_COLLECTION_NAME}/${surveyId}/submissions`,
        submission.id
      ),
      FirebaseDataConverter.submissionToJS(submission)
    );
  }

  /**
   * Returns a stream containing the user with the specified id. Remote changes
   * to the user will cause a new value to be emitted.
   *
   * @param uid the unique id used to represent the user in the data store.
   */
  user$(uid: string): Observable<User | undefined> {
    return docData(doc(this.db, 'users', uid)).pipe(
      map(data => FirebaseDataConverter.toUser(data as DocumentData, uid))
    );
  }

  /**
   * Returns a promise resolving to true if the user's email is either
   * explicitly listed as a document ID in the 'passlist' collection or
   * matches the regular expression stored in the 'passlist/regexp' document.
   *
   * @param userEmail The email of the user to check against the passlist.
   */
  async isPasslisted(userEmail: string): Promise<boolean> {
    const docSnapshot = await getDoc(doc(this.db, 'passlist', userEmail));

    if (docSnapshot.exists()) return true;

    const regexpSnapshot = await getDoc(doc(this.db, 'passlist', 'regexp'));

    if (regexpSnapshot.exists()) {
      const regexpData = regexpSnapshot.data() as
        | { regexp?: string }
        | undefined;

      const regexpString = regexpData?.regexp;

      if (regexpString) {
        const regex = new RegExp(regexpString);

        return regex.test(userEmail);
      }
    }

    return false;
  }

  private toLocationsOfInterest(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    loiIds: any[]
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
   * @param canViewAll a flag indicating whether the user has permission to view all LOIs for this survey.
   */
  getAccessibleLois$(
    { id: surveyId }: Survey,
    userId: string,
    canViewAll: boolean
  ): Observable<List<LocationOfInterest>> {
    if (canViewAll) {
      return collectionData(
        collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`),
        { idField: 'id' }
      ).pipe(map(docs => this.toLocationsOfInterest(docs)));
    }

    const importedLois = collectionData(
      query(
        collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`),
        where(l.source, '==', Source.IMPORTED)
      ),
      { idField: 'id' }
    );

    const fieldDataLois = collectionData(
      query(
        collection(this.db, `${SURVEYS_COLLECTION_NAME}/${surveyId}/lois`),
        where(l.source, '==', Source.FIELD_DATA),
        where(l.ownerId, '==', userId)
      ),
      { idField: 'id' }
    );

    return combineLatest([importedLois, fieldDataLois]).pipe(
      map(([predefinedLois, fieldDataLois]) =>
        this.toLocationsOfInterest(
          (predefinedLois as any[]).concat(fieldDataLois)
        )
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
   * @param canViewAll a flag indicating whether the user has permission to view all LOIs for this survey.
   */
  getAccessibleSubmissions$(
    survey: Survey,
    loi: LocationOfInterest,
    userId: string,
    canViewAll: boolean
  ): Observable<List<Submission>> {
    const submissionsRef = collection(
      this.db,
      `${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`
    );
    const q = this.canViewSubmissions(
      submissionsRef,
      loi.id,
      userId,
      canViewAll
    );
    return collectionData(q, { idField: 'id' }).pipe(
      map(array =>
        List(
          array
            .map(obj =>
              submissionDocToModel(survey.getJob(loi.jobId)!, obj['id'], obj)
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
    return from(
      getDoc(
        doc(
          this.db,
          `${SURVEYS_COLLECTION_NAME}/${survey.id}/submissions`,
          submissionId
        )
      )
    ).pipe(
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
    return doc(collection(this.db, 'ids')).id;
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
    const { email: ownerEmail, id: ownerId } = user;
    const acl = Map<string, Role>({ [ownerEmail]: Role.SURVEY_ORGANIZER });
    await setDoc(
      doc(this.db, SURVEYS_COLLECTION_NAME, surveyId),
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

  async copySurvey(surveyId: string): Promise<string> {
    const newSurveyId = this.generateId();

    const surveyDoc = await getDoc(
      doc(this.db, SURVEYS_COLLECTION_NAME, surveyId)
    );

    const survey = surveyDocToModel(surveyId, surveyDoc.data() as DocumentData);

    if (survey instanceof Error) return '';

    const newSurvey = surveyToDocument(newSurveyId, {
      ...survey,
      id: newSurveyId,
      title: 'Copy of ' + survey.title,
      acl: survey.acl.filter(role => role === Role.SURVEY_ORGANIZER),
    });

    await setDoc(doc(this.db, SURVEYS_COLLECTION_NAME, newSurveyId), newSurvey);

    const jobsCollectionRef = collection(
      this.db,
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/${JOBS_COLLECTION_NAME}`
    );

    const qs = await getDocs(jobsCollectionRef);

    qs.forEach(async (jobDoc: QueryDocumentSnapshot<DocumentData>) => {
      const newJobId = this.generateId();

      const job = jobDocToModel(jobDoc.data());

      const newJob = jobToDocument({
        ...job,
        id: newJobId,
      } as Job);

      await setDoc(
        doc(
          this.db,
          `${SURVEYS_COLLECTION_NAME}/${newSurveyId}/${JOBS_COLLECTION_NAME}`,
          newJobId
        ),
        newJob
      );
    });

    return newSurveyId;
  }

  getImageDownloadURL(path: string) {
    return getDownloadURL(ref(getStorage(), path));
  }

  async getTermsOfService(): Promise<string> {
    const tos = await getDoc(doc(this.db, 'config', 'tos'));
    return tos.get('text');
  }

  async getAccessDeniedMessage(): Promise<{ message?: string; link?: string }> {
    const accessDeniedMessage = await getDoc(
      doc(this.db, 'config', 'accessDenied')
    );
    return {
      message: accessDeniedMessage.get('message'),
      link: accessDeniedMessage.get('link'),
    };
  }

  /**
   * Converts list of tasks to ordered map.
   */
  convertTasksListToMap(tasks: List<Task>): Map<string, Task> {
    let tasksMap = OrderedMap<string, Task>();
    tasks.forEach((task: Task) => {
      const taskId = task.id ? task.id : this.generateId();
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
    canViewAll: boolean
  ) {
    const q = query(ref, where(sb.loiId, '==', loiId));

    return canViewAll ? q : query(q, where(sb.ownerId, '==', userId));
  }
}
