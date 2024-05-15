import {Location} from '@angular/common';
import {Component} from '@angular/core';

@Component({
  selector: 'ground-about-page',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss'],
})
export class AboutComponent {
  constructor(private _location: Location) {}

  onBackButtonClick() {
    this._location.back();
  }
}
