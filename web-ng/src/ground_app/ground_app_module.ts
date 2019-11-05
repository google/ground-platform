import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './routing_module';
import { GroundApp } from './ground_app';
import { GroundMapsModule } from '../ground_maps/ground_maps_module';

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
