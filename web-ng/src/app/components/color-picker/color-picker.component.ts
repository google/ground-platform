import { Component, ElementRef, OnInit, Inject, Output, EventEmitter } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialog, MatDialogConfig } from "@angular/material";
import { ColorEvent } from 'ngx-color';

@Component({
  selector: 'app-color-picker',
  templateUrl: './color-picker.component.html',
  styleUrls: ['./color-picker.component.css']
})
export class ColorPickerComponent implements OnInit {

  private readonly _matDialogRef: MatDialogRef<ColorPickerComponent>;
  private readonly triggerElementRef: ElementRef;
  @Output() public onColorPicked: EventEmitter<{}> = new EventEmitter();
  constructor(private dialog: MatDialog, _matDialogRef: MatDialogRef<ColorPickerComponent>,
    @Inject(MAT_DIALOG_DATA) data: { trigger: ElementRef }) {
    this._matDialogRef = _matDialogRef;
    this.triggerElementRef = data.trigger;
  }

  ngOnInit() {
    if (this.triggerElementRef) {
      const matDialogConfig: MatDialogConfig = new MatDialogConfig();
      const rect = this.triggerElementRef.nativeElement.getBoundingClientRect();
      matDialogConfig.position = { left: `${rect.left}px`, top: `${rect.bottom + 10}px` };
      matDialogConfig.width = '300px';
      matDialogConfig.height = '400px';
      this._matDialogRef.updateSize(matDialogConfig.width, matDialogConfig.height);
      this._matDialogRef.updatePosition(matDialogConfig.position);
    }
  }

  openColorPickerDialog() {
    this.dialog.open(ColorPickerComponent);
  }

  close() {
    this._matDialogRef.close();
  }

  handleColorChange($event: ColorEvent) {
    this.onColorPicked.emit($event.color);
  }

}
