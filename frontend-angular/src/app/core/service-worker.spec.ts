import { registerServiceWorker, SW_UPDATE_INTERVAL_MS } from './service-worker';

describe('registerServiceWorker', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('registriert /push-sw.js und prüft stündlich auf Updates', async () => {
    vi.useFakeTimers();
    const update = vi.fn();
    const register = vi.fn().mockResolvedValue({ update });

    await registerServiceWorker({ register });

    expect(register).toHaveBeenCalledWith('/push-sw.js');
    vi.advanceTimersByTime(SW_UPDATE_INTERVAL_MS);
    expect(update).toHaveBeenCalledTimes(1);
  });

  it('tut ohne Service-Worker-Unterstützung nichts', async () => {
    await expect(registerServiceWorker(undefined)).resolves.toBeUndefined();
  });

  it('protokolliert einen Registrierungsfehler, ohne zu werfen', async () => {
    const error = vi.spyOn(console, 'error').mockReturnValue();
    const register = vi.fn().mockRejectedValue(new Error('nein'));

    await registerServiceWorker({ register });

    expect(error).toHaveBeenCalled();
  });
});
