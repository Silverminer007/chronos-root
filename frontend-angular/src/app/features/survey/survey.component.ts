import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { getErrorStatus } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';

/** Wartezeit nach dem Absenden, bevor zur Agenda weitergeleitet wird. */
const REDIRECT_DELAY_MS = 2000;
const Q2_MAX_WORDS = 30;
const Q5_MAX_WORDS = 20;
/** Antwort, die alle anderen Antworten der Frage ausschließt. */
const NONE = 'none';

interface Option {
  value: string;
  label: string;
}

export const Q1_OPTIONS: Option[] = [
  { value: 'plan_event', label: 'Einen neuen Termin planen' },
  { value: 'check_attendance', label: 'Nachschauen, wer zu einem Termin kommt' },
  { value: 'respond_invite', label: 'Rückmeldung zu einem Termin geben' },
  { value: 'message', label: 'Eine Nachricht zu einem Termin lesen oder senden' },
  { value: 'manage_group', label: 'Eine Gruppe oder Kontakte verwalten' },
  { value: 'other', label: 'Etwas anderes' },
];

export const Q3_OPTIONS: Option[] = [
  { value: 'friends', label: 'Jemanden als Freund hinzufügen' },
  { value: 'groups', label: 'Eine Gruppe erstellen oder nutzen' },
  { value: 'messaging', label: 'Innerhalb eines Termins schreiben' },
  { value: 'notifications', label: 'Push-Benachrichtigungen als Erinnerungen erhalten' },
  { value: 'rsvp', label: 'Zu einem Termin zu- oder absagen' },
  { value: NONE, label: 'Keines davon' },
];

export const Q4_RESPONDING: Option[] = [
  { value: 'maybe_option', label: "Eine 'Vielleicht / unverbindlich'-Option" },
  { value: 'suggest_time', label: 'Einen anderen Zeitvorschlag machen können' },
  { value: 'response_note', label: 'Beim Antworten eine kurze Notiz hinzufügen' },
];

export const Q4_ORGANIZING: Option[] = [
  { value: 'know_lateness', label: 'Wissen, ob ein Teilnehmer später kommt oder früher geht' },
  { value: 'know_window', label: "Das genaue Zeitfenster eines Teilnehmers kennen (z. B. 'nur 18:00–20:00 Uhr')" },
  { value: 'know_contribution', label: 'Wissen, was ein Teilnehmer beisteuern möchte (Essen, Aufgabe usw.)' },
  { value: NONE, label: 'Keines davon – Zu-/Absagen reicht mir' },
];

/** Anzahl der durch Leerraum getrennten Wörter. */
export function countWords(text: string): number {
  const trimmed = text.trim();
  return trimmed ? trimmed.split(/\s+/).length : 0;
}

/** Schaltet eine Mehrfachantwort um; „Keines davon“ ersetzt alle anderen und wird von ihnen ersetzt. */
function toggle(selected: string[], value: string): string[] {
  if (value === NONE) return selected.includes(NONE) ? [] : [NONE];
  if (selected.includes(value)) return selected.filter((v) => v !== value);
  return [...selected.filter((v) => v !== NONE), value];
}

/** Umfrage-Seite `/survey/:id` (Port von `pages/survey/[surveyId].vue`). */
@Component({
  selector: 'app-survey',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, RouterLink, FormsModule],
  templateUrl: './survey.component.html',
  styleUrl: './survey.component.scss',
})
export class SurveyComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute).snapshot;

  private readonly surveyId = this.route.paramMap.get('id') ?? '';
  private readonly fromPush = this.route.queryParamMap.get('from') === 'push';

  protected readonly q1Options = Q1_OPTIONS;
  protected readonly q3Options = Q3_OPTIONS;
  protected readonly q4Responding = Q4_RESPONDING;
  protected readonly q4Organizing = Q4_ORGANIZING;
  protected readonly q2Max = Q2_MAX_WORDS;
  protected readonly q5Max = Q5_MAX_WORDS;

  protected readonly alreadyCompleted = signal(false);
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);

  protected readonly q1 = signal('');
  protected readonly q1Other = signal('');
  protected readonly q2 = signal('');
  protected readonly q3 = signal<string[]>([]);
  protected readonly q4 = signal<string[]>([]);
  protected readonly q5 = signal('');

  protected readonly q2Words = computed(() => countWords(this.q2()));
  protected readonly q5Words = computed(() => countWords(this.q5()));

  async ngOnInit(): Promise<void> {
    // Aus einer Push-Benachrichtigung heraus entfällt die Prüfung (wie in Nuxt); Prüffehler zeigen das Formular.
    if (this.fromPush) return;
    try {
      const eligible = await firstValueFrom(this.http.get<boolean>(`/api/v2/survey/${this.surveyId}/eligible`));
      if (eligible === false) this.alreadyCompleted.set(true);
    } catch {
      // Formular anzeigen
    }
  }

  protected toggleQ3(value: string): void {
    this.q3.update((selected) => toggle(selected, value));
  }

  protected toggleQ4(value: string): void {
    this.q4.update((selected) => toggle(selected, value));
  }

  protected async submit(): Promise<void> {
    this.submitting.set(true);
    const answers: Record<string, unknown> = {};
    if (this.q1()) {
      answers['q1'] = this.q1();
      if (this.q1() === 'other' && this.q1Other().trim()) answers['q1_other'] = this.q1Other().trim();
    }
    if (this.q2().trim()) answers['q2'] = this.q2().trim();
    if (this.q3().length) answers['q3'] = [...this.q3()];
    if (this.q4().length) answers['q4'] = [...this.q4()];
    if (this.q5().trim()) answers['q5'] = this.q5().trim();

    try {
      await firstValueFrom(this.http.post(`/api/v2/survey/${this.surveyId}/responses`, { answers }));
      this.submitted.set(true);
      setTimeout(() => this.router.navigateByUrl('/agenda'), REDIRECT_DELAY_MS);
    } catch (err) {
      if (getErrorStatus(err) === 400) {
        this.alreadyCompleted.set(true);
      } else {
        this.toast.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Antworten konnten nicht gesendet werden. Bitte versuche es erneut.',
          life: 4000,
        });
      }
    } finally {
      this.submitting.set(false);
    }
  }
}
