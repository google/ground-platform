import { Component } from '@angular/core';

@Component({
  selector: 'ground-maps',
  templateUrl: './ground_maps.ng.html',
  styleUrls: ['./ground_maps.css']
})
export class GroundMaps {
  title = 'This is a Ground Map';
  lat: number = 40.767716;
  lng: number = -73.971714;
}
