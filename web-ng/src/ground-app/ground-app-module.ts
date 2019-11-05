import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './routing-module';
import { GroundApp } from './ground-app';
import { GroundMapsModule } from '../ground-maps/ground-maps-module';

@NgModule({
  declarations: [
    GroundApp
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    GroundMapsModule
  ],
  bootstrap: [GroundApp]
})
export class AppModule { }
