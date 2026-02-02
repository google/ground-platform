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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { CreateSurveyComponent } from 'app/components/create-survey/create-survey.component';
import { CreateSurveyModule } from 'app/components/create-survey/create-survey.module';
import { MainPageContainerComponent } from 'app/components/main-page-container/main-page-container.component';
import { MainPageContainerModule } from 'app/components/main-page-container/main-page-container.module';
import { SignInPageComponent } from 'app/components/shared/sign-in-page/sign-in-page.component';
import { SignInPageModule } from 'app/components/shared/sign-in-page/sign-in-page.module';
import { SurveyListComponent } from 'app/components/shared/survey-list/survey-list.component';
import { SurveyListModule } from 'app/components/shared/survey-list/survey-list.module';
import { AuthGuard } from 'app/services/auth/auth.guard';
import { passlistGuard } from 'app/services/auth/passlist.guard';
import {
  ABOUT,
  ANDROID_SEGMENT,
  ERROR,
  LOI_ID,
  LOI_SEGMENT,
  SIGN_IN_SEGMENT,
  SUBMISSION_ID,
  SUBMISSION_SEGMENT,
  SURVEYS_CREATE,
  SURVEYS_EDIT,
  SURVEYS_SEGMENT,
  SURVEY_ID,
  SURVEY_SEGMENT,
  TASK_ID,
  TASK_SEGMENT,
  TERMS,
} from 'app/services/navigation/navigation.constants';

import { AboutComponent } from './components/about/about.component';
import { AboutModule } from './components/about/about.module';
import { AndroidIntentLandingPageComponent } from './components/android-intent-landing-page/android-intent-landing-page.component';
import { AndroidIntentLandingPageModule } from './components/android-intent-landing-page/android-intent-landing-page.module';
import { EditDetailsComponent } from './components/edit-survey/edit-details/edit-details.component';
import { EditDetailsModule } from './components/edit-survey/edit-details/edit-details.module';
import { EditJobComponent } from './components/edit-survey/edit-job/edit-job.component';
import { EditJobModule } from './components/edit-survey/edit-job/edit-job.module';
import { EditSurveyComponent } from './components/edit-survey/edit-survey.component';
import { EditSurveyModule } from './components/edit-survey/edit-survey.module';
import { SurveyJsonComponent } from './components/edit-survey/survey-json/survey-json.component';
import { SurveyJsonModule } from './components/edit-survey/survey-json/survey-json.module';
import { ErrorComponent } from './components/error/error.component';
import { ErrorModule } from './components/error/error.module';
import { ShareSurveyComponent } from './components/shared/share-survey/share-survey.component';
import { TermsComponent } from './components/terms/terms.component';
import { TermsModule } from './components/terms/terms.module';

const routes: Routes = [
  {
    path: '',
    redirectTo: `${SURVEYS_SEGMENT}`,
    pathMatch: 'full',
  },
  {
    path: SIGN_IN_SEGMENT,
    component: SignInPageComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${SURVEYS_SEGMENT}`,
    component: SurveyListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${SURVEYS_SEGMENT}/:${SURVEY_ID}/${SURVEYS_CREATE}`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${SURVEYS_SEGMENT}/${SURVEYS_CREATE}`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard, passlistGuard],
  },
  {
    path: `${SURVEY_SEGMENT}/:${SURVEY_ID}/${SURVEYS_EDIT}`,
    component: EditSurveyComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'job/:id', component: EditJobComponent },
      { path: 'survey', component: EditDetailsComponent },
      { path: 'share', component: ShareSurveyComponent },
      { path: 'json', component: SurveyJsonComponent },
    ],
  },
  {
    path: `${SURVEY_SEGMENT}/:${SURVEY_ID}`,
    component: MainPageContainerComponent,
    canActivate: [AuthGuard],
    children: [
      {
        path: `${LOI_SEGMENT}/:${LOI_ID}`,
        component: MainPageContainerComponent,
        children: [
          {
            path: `${SUBMISSION_SEGMENT}/:${SUBMISSION_ID}`,
            component: MainPageContainerComponent,
            children: [
              {
                path: `${TASK_SEGMENT}/:${TASK_ID}`,
                component: MainPageContainerComponent,
              },
            ],
          },
        ],
      },
    ],
  },
  {
    path: ERROR,
    component: ErrorComponent,
    canActivate: [AuthGuard],
  },
  {
    path: ABOUT,
    component: AboutComponent,
  },
  {
    path: `${ANDROID_SEGMENT}`,
    component: AndroidIntentLandingPageComponent,
    children: [{ path: '**', component: AndroidIntentLandingPageComponent }],
  },
  {
    path: TERMS,
    component: TermsComponent,
    canActivate: [AuthGuard],
  },
];
const config = RouterModule.forRoot(routes, { bindToComponentInputs: true });

@NgModule({
  imports: [
    config,
    AboutModule,
    AndroidIntentLandingPageModule,
    CreateSurveyModule,
    EditDetailsModule,
    EditJobModule,
    EditSurveyModule,
    ErrorModule,
    MainPageContainerModule,
    SignInPageModule,
    SurveyJsonModule,
    SurveyListModule,
    TermsModule,
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}
