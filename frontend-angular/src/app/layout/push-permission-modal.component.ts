import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { PushService } from '../core/push.service';

/** Fragt einmalig (dann erneut nach 7 Tagen) nach der Push-Berechtigung (Port von `PushPermissionModal.vue`). */
@Component({
  selector: 'app-push-permission-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="push-permission-title">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h2 class="modal-title fs-5" id="push-permission-title">Push-Benachrichtigungen aktivieren?</h2>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="dismiss()"></button>
            </div>
            <div class="modal-body">
              <p>Du bekommst so Terminerinnerungen, bekommst Neuigkeiten zu Terminen und wirst an Abstimmungen erinnert</p>
              <p class="mb-0">Wir benötigen von dir die Erlaubnis der Benachrichtigungen zu senden</p>
            </div>
            <div class="modal-footer justify-content-start">
              <button type="button" class="btn btn-gradient" (click)="enable()">Aktivieren</button>
              <button type="button" class="btn btn-link" (click)="deny()">Nicht jetzt</button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class PushPermissionModalComponent implements OnInit {
  private readonly push = inject(PushService);

  protected readonly visible = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      this.visible.set(await this.push.shouldAsk());
    } catch (err) {
      // Die Abo-Prüfung darf die Seite nicht stören; beim nächsten Besuch läuft sie erneut.
      console.error('Push-Status konnte nicht geprüft werden', err);
    }
  }

  protected async enable(): Promise<void> {
    try {
      await this.push.subscribe();
      this.push.markAsked('granted');
    } catch {
      // Der Browser hat die Berechtigung abgelehnt oder das Abo scheiterte.
      this.push.markAsked('denied');
    }
    this.visible.set(false);
  }

  protected deny(): void {
    this.push.markAsked('denied');
    this.visible.set(false);
  }

  protected dismiss(): void {
    this.push.markAsked('dismissed');
    this.visible.set(false);
  }
}
