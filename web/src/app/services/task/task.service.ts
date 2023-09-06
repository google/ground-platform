import { Injectable } from '@angular/core';
import { MultipleChoice } from 'app/models/task/multiple-choice.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from '../data-store/data-store.service';
import { List } from 'immutable';
import { Observable, of, switchMap } from 'rxjs';
import { SurveyService } from '../survey/survey.service';
import { JobService } from '../job/job.service';

export type TaskUpdate = {
  label: string,
  required: boolean,
  taskType: TaskType,
  multipleChoice: MultipleChoice,
  index: number,
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private tasks$: Observable<List<Task>>;

  constructor(
    private dataStoreService: DataStoreService,
    private surveyService: SurveyService,
    private dataStore: DataStoreService,
  ) {
    this.tasks$ = this.surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey => {
          let jobId = survey.jobs.values().next().value.id;
          return this.dataStore.tasks$(survey.id, jobId)
        })
      );
  }

  /**
   * Creates and returns a new task with a generated unique identifier and a single English label.
   */
  createTask(
    type: TaskType,
    label: string,
    required: boolean,
    index: number,
    multipleChoice?: MultipleChoice
  ): Task {
    const taskId = this.dataStoreService.generateId();
    return new Task(taskId, type, label, required, index, multipleChoice);
  }

  getTasks$(): Observable<List<Task>> {
    return this.tasks$;
  }

  addOrUpdateTasks(surveyId: string, jobId: string, tasks: List<Task>): Promise<void> {
    return this.dataStoreService.addOrUpdateTasks(surveyId, jobId, tasks)
  }
}
