import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { GroundMaps } from '../ground-maps/ground-maps';

const routes: Routes = [{path: '', component: GroundMaps}];
const config = RouterModule.forRoot(routes);

@NgModule({
  imports: [config],
  exports: [RouterModule]
})
export class AppRoutingModule { }
