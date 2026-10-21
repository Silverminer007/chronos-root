import { appointment, participant } from './testing';
import { isResponsible, roleLabel, sortByStatusThenName, statusLabel } from './participation';

describe('participation', () => {
  it('übersetzt Rollen und Status ins Deutsche', () => {
    expect(roleLabel('RESPONSIBLE')).toBe('Organisator');
    expect(roleLabel('ATTENDANT')).toBe('Teilnehmer');
    expect(roleLabel('HELPER')).toBe('Helfer');
    expect(roleLabel('GUEST')).toBe('Gast');
    expect(roleLabel('NONE')).toBe('');
    expect(statusLabel('APPROVED')).toBe('Zugesagt');
    expect(statusLabel('REJECTED')).toBe('Abgesagt');
    expect(statusLabel('PENDING')).toBe('Ausstehend');
  });

  it('erkennt Organisatoren am Benutzer und der Rolle RESPONSIBLE', () => {
    const a = appointment({ participants: [participant(1, { role: 'RESPONSIBLE' }), participant(2)] });
    expect(isResponsible(a, 1)).toBe(true);
    expect(isResponsible(a, 2)).toBe(false);
    expect(isResponsible(a, undefined)).toBe(false);
  });

  it('sortiert nach Status (zugesagt, abgesagt, ausstehend), dann nach Name', () => {
    const sorted = sortByStatusThenName([
      participant(1, { name: 'Zoe', status: 'PENDING' }),
      participant(2, { name: 'Bea', status: 'APPROVED' }),
      participant(3, { name: 'Al', status: 'REJECTED' }),
      participant(4, { name: 'Ada', status: 'APPROVED' }),
    ]);
    expect(sorted.map((p) => p.name)).toEqual(['Ada', 'Bea', 'Al', 'Zoe']);
  });
});
