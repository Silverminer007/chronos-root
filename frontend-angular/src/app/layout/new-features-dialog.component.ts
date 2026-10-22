import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DialogQueueService } from './dialog-queue.service';
import { NewFeature, NewFeaturesService } from '../core/new-features.service';

/** Dauer des Ausblendens, bevor der Dialog entfernt wird. */
export const LEAVE_DELAY_MS = 200;

/** Zeigt Neuigkeiten, die der Nutzer noch nicht gesehen hat (Port von `NewFeaturesDialog.vue`). */
@Component({
  selector: 'app-new-features-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-features-dialog.component.html',
  styleUrl: './new-features-dialog.component.scss',
})
export class NewFeaturesDialogComponent implements OnInit {
  private readonly newFeatures = inject(NewFeaturesService);
  private readonly router = inject(Router);
  private readonly queue = inject(DialogQueueService);

  protected readonly features = signal<NewFeature[]>([]);
  protected readonly currentStep = signal(0);
  private readonly wanted = signal(false);
  protected readonly visible = computed(() => this.wanted() && this.queue.active() === 'features');
  protected readonly leaving = signal(false);

  protected readonly currentFeature = computed<NewFeature | undefined>(() => this.features()[this.currentStep()]);
  protected readonly isFirst = computed(() => this.currentStep() === 0);
  protected readonly isLast = computed(() => this.currentStep() === this.features().length - 1);

  ngOnInit(): void {
    this.features.set(this.newFeatures.getUnshownFeatures());
    this.wanted.set(this.features().length > 0);
    if (this.wanted()) this.queue.claim('features');
  }

  protected next(): void {
    const feature = this.currentFeature();
    if (!feature) return;
    this.newFeatures.markShown(feature.id);
    if (this.isLast()) {
      this.close();
    } else {
      this.currentStep.update((step) => step + 1);
    }
  }

  protected back(): void {
    if (!this.isFirst()) this.currentStep.update((step) => step - 1);
  }

  protected skipAll(): void {
    this.newFeatures.markAllShown();
    this.close();
  }

  protected goToLink(): void {
    const feature = this.currentFeature();
    if (!feature?.link) return;
    this.newFeatures.markShown(feature.id);
    this.close();
    void this.router.navigateByUrl(feature.link);
  }

  private close(): void {
    this.leaving.set(true);
    setTimeout(() => {
      this.wanted.set(false);
      this.queue.release('features');
      this.leaving.set(false);
    }, LEAVE_DELAY_MS);
  }
}
