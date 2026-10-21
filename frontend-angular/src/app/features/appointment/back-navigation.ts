import { Location } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

/** „Zurück“ der Detailseite (Port von `goBack`): Browser-Verlauf, sonst die Agenda (z. B. bei geteiltem Link). */
@Injectable({ providedIn: 'root' })
export class BackNavigation {
  private readonly location = inject(Location);
  private readonly router = inject(Router);

  back(): void {
    // Der Router legt `navigationId` in den History-State; ab dem zweiten Eintrag gibt es ein Zurück innerhalb der App.
    if (((history.state as { navigationId?: number } | null)?.navigationId ?? 0) > 1) {
      this.location.back();
    } else {
      void this.router.navigateByUrl('/agenda');
    }
  }
}
