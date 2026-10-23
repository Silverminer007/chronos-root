import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ConfirmDialogComponent } from './core/confirm-dialog.component';
import { ThemeService } from './core/theme.service';
import { ToastContainerComponent } from './core/toast-container.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainerComponent, ConfirmDialogComponent],
  template: '<router-outlet /><app-toast-container /><app-confirm-dialog />',
})
export class App {
  constructor() {
    inject(ThemeService).init();
    inject(AuthService).listenForSessionExpiry();
  }
}
