import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { BrowserModule } from '@angular/platform-browser';
import { MatDialogModule } from '@angular/material/dialog';
import { FeatureDetailsComponent } from './feature-details.component';

@NgModule({
  declarations: [FeatureDetailsComponent],
  imports: [
    BrowserModule,
    MatListModule,
    CommonModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDialogModule,
  ],
  exports: [FeatureDetailsComponent],
})
export class FeatureDetailsModule {}
