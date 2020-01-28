import { Component, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {MatDialog, MatDialogConfig} from "@angular/material";

@Component({
  selector: 'app-color-picker',
  templateUrl: './color-picker.component.html',
  styleUrls: ['./color-picker.component.css']
})
export class ColorPickerComponent implements OnInit {


  constructor(private dialog: MatDialog) { }

  ngOnInit() {
  }

  openColorPickerDialog() {
   this.dialog.open(ColorPickerComponent);
  }

}
