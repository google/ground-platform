import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class HamburgerService {
  private sidePanelExpanded: boolean;

  constructor() {
    this.sidePanelExpanded = true;
  }

  getSidePanelExpanded(): boolean {
    return this.sidePanelExpanded;
  }

  hamburgerEvent() {
    this.sidePanelExpanded = !this.sidePanelExpanded;
  }
}
