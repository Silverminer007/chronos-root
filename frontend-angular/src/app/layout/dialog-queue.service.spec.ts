import { TestBed } from '@angular/core/testing';
import { DialogQueueService } from './dialog-queue.service';

describe('DialogQueueService', () => {
  let queue: DialogQueueService;

  beforeEach(() => {
    queue = TestBed.inject(DialogQueueService);
  });

  it('hat ohne Anmeldung keinen aktiven Dialog', () => {
    expect(queue.active()).toBeUndefined();
  });

  it('lässt den Push-Dialog vor den Neuigkeiten und der PWA-Installation dran', () => {
    queue.claim('pwaInstall');
    queue.claim('features');
    queue.claim('push');
    expect(queue.active()).toBe('push');
    queue.release('push');
    expect(queue.active()).toBe('features');
    queue.release('features');
    expect(queue.active()).toBe('pwaInstall');
    queue.release('pwaInstall');
    expect(queue.active()).toBeUndefined();
  });
});
