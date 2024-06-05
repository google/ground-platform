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

import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {SignInPageComponent} from 'app/components/sign-in-page/sign-in-page.component';
import {SignInPageModule} from 'app/components/sign-in-page/sign-in-page.module';
import {SurveyListComponent} from 'app/components/survey-list/survey-list.component';
import {SurveyListModule} from 'app/components/survey-list/survey-list.module';
import {CreateSurveyComponent} from 'app/pages/create-survey/create-survey.component';
import {CreateSurveyModule} from 'app/pages/create-survey/create-survey.module';
import {MainPageContainerComponent} from 'app/pages/main-page-container/main-page-container.component';
import {MainPageContainerModule} from 'app/pages/main-page-container/main-page-container.module';
import {AuthGuard} from 'app/services/auth/auth.guard';
import {NavigationService} from 'app/services/navigation/navigation.service';

import {ShareSurveyComponent} from './components/share-survey/share-survey.component';
import {AboutComponent} from './pages/about/about.component';
import {EditDetailsComponent} from './pages/edit-survey/edit-details/edit-details.component';
import {EditJobComponent} from './pages/edit-survey/edit-job/edit-job.component';
import {EditSurveyComponent} from './pages/edit-survey/edit-survey.component';
import {EditSurveyModule} from './pages/edit-survey/edit-survey.module';
import {SurveyJsonComponent} from './pages/edit-survey/survey-json/survey-json.component';
import {ErrorComponent} from './pages/error/error.component';
import {ErrorModule} from './pages/error/error.module';

const routes: Routes = [
  {
    path: '',
    redirectTo: `${NavigationService.SURVEYS_SEGMENT}`,
    pathMatch: 'full',
  },
  {
    path: NavigationService.SIGN_IN_SEGMENT,
    component: SignInPageComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}`,
    component: SurveyListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}/:${NavigationService.SURVEY_ID}/${NavigationService.SURVEYS_CREATE}`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}/${NavigationService.SURVEYS_CREATE}`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEY_SEGMENT}/:${NavigationService.SURVEY_ID}/${NavigationService.SURVEYS_EDIT}`,
    component: EditSurveyComponent,
    canActivate: [AuthGuard],
    children: [
      {path: 'job/:id', component: EditJobComponent},
      {path: 'survey', component: EditDetailsComponent},
      {path: 'share', component: ShareSurveyComponent},
      {path: 'json', component: SurveyJsonComponent},
    ],
  },
  {
    path: `${NavigationService.SURVEY_SEGMENT}/:${NavigationService.SURVEY_ID}`,
    component: MainPageContainerComponent,
    canActivate: [AuthGuard],
  },
  {
    path: NavigationService.ERROR,
    component: ErrorComponent,
    canActivate: [AuthGuard],
  },
  {
    path: NavigationService.ABOUT,
    component: AboutComponent,
    canActivate: [AuthGuard],
  },
];
const config = RouterModule.forRoot(routes, {});

@NgModule({
  imports: [config],
  exports: [
    MainPageContainerModule,
    RouterModule,
    SignInPageModule,
    SurveyListModule,
    CreateSurveyModule,
    EditSurveyModule,
    ErrorModule,
  ],
})
export class AppRoutingModule {}
