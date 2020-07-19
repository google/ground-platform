import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'user-avatar',
  templateUrl: './user-avatar.component.html',
  styleUrls: ['./user-avatar.component.scss'],
})
export class UserAvatarComponent implements OnInit {
  @Input() displayText?: string;

  constructor() {}

  ngOnInit(): void {}
}
