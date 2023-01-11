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

import {NgModule} from '@angular/core';
import {Routes, RouterModule} from '@angular/router';
import {MainPageContainerComponent} from 'app/pages/main-page-container/main-page-container.component';
import {AuthGuard} from 'app/services/auth/auth.guard';
import {SignInPageComponent} from 'app/components/sign-in-page/sign-in-page.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyListComponent} from 'app/components/survey-list/survey-list.component';
import {MainPageContainerModule} from 'app/pages/main-page-container/main-page-container.module';
import {SignInPageModule} from 'app/components/sign-in-page/sign-in-page.module';
import {SurveyListModule} from 'app/components/survey-list/survey-list.module';
import {CreateSurveyComponent} from 'app/pages/create-survey/create-survey.component';
import {CreateSurveyModule} from 'app/pages/create-survey/create-survey.module';

const routes: Routes = [
  {
    path: NavigationService.SIGN_IN_SEGMENT,
    component: SignInPageComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEY_SEGMENT}/:${NavigationService.SURVEY_ID}`,
    component: MainPageContainerComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}`,
    component: SurveyListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}/create`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard],
  },
  {
    path: `${NavigationService.SURVEYS_SEGMENT}/:${NavigationService.SURVEY_ID}/${NavigationService.SURVEYS_CREATE}`,
    component: CreateSurveyComponent,
    canActivate: [AuthGuard],
  },
];
const config = RouterModule.forRoot(routes, {
  relativeLinkResolution: 'legacy',
});

@NgModule({
  imports: [config],
  exports: [
    MainPageContainerModule,
    RouterModule,
    SignInPageModule,
    SurveyListModule,
    CreateSurveyModule,
  ],
})
export class AppRoutingModule {}
