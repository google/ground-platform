import { Injectable } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  constructor(private readonly snackBar: MatSnackBar) {}

  private config: MatSnackBarConfig = {
    duration: 10000,
    horizontalPosition: 'right',
    verticalPosition: 'top',
  };

  success(message: string): void {
    this.config['panelClass'] = ['success', 'notification'];
    this.snackBar.open(message, /* action= */ '', this.config);
  }

  error(message: string): void {
    this.config['panelClass'] = ['error', 'notification'];
    this.snackBar.open(message, /* action= */ '', this.config);
  }
}
