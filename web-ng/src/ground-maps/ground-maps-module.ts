import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { GroundMaps } from './ground-maps';

import { AgmCoreModule } from '@agm/core';

@NgModule({
  imports: [
    BrowserModule,
    AgmCoreModule.forRoot({
      // TODO: load apikey from local config file
      apiKey: '',
    }),
  ],
  declarations: [GroundMaps],
  bootstrap: [GroundMaps],
})
export class GroundMapsModule {}
