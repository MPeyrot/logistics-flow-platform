import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { ReplayProgress } from './dlt.model';
import { REPLAY_ID_KEY, ReplayTracker } from './replay-tracker';

const progress = (over: Partial<ReplayProgress>): ReplayProgress => ({
  id: 'job-1',
  finished: false,
  replayed: 0,
  published: 0,
  failed: 0,
  error: null,
  ...over,
});

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), ReplayTracker],
  });
  return { tracker: TestBed.inject(ReplayTracker), http: TestBed.inject(HttpTestingController) };
}

describe('ReplayTracker', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
  });
  afterEach(() => vi.useRealTimers());

  it('garde l’identifiant, interroge chaque seconde et l’efface à la fin', () => {
    const { tracker, http } = setup();
    tracker.start();
    http
      .expectOne({ method: 'POST', url: '/api/admin/dlt/scan-events/replay' })
      .flush(progress({}), {
        status: 202,
        statusText: 'Accepted',
      });
    expect(localStorage.getItem(REPLAY_ID_KEY)).toBe('job-1');

    vi.advanceTimersByTime(0);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/job-1')
      .flush(progress({ replayed: 2, published: 1 }));
    expect(tracker.progress()).toMatchObject({ replayed: 2, published: 1, finished: false });

    vi.advanceTimersByTime(1000);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/job-1')
      .flush(progress({ replayed: 3, published: 3, finished: true }));
    expect(tracker.progress()?.finished).toBe(true);
    expect(localStorage.getItem(REPLAY_ID_KEY)).toBeNull();

    vi.advanceTimersByTime(5000);
    http.expectNone('/api/admin/dlt/scan-events/replay/job-1');
  });

  it('reprend le suivi de l’identifiant gardé après un rechargement', () => {
    localStorage.setItem(REPLAY_ID_KEY, 'job-9');
    const { tracker, http } = setup();
    tracker.resume();
    vi.advanceTimersByTime(0);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/job-9')
      .flush(progress({ id: 'job-9', replayed: 5 }));
    expect(tracker.progress()?.replayed).toBe(5);
    tracker.stop();
  });

  it('ne fait rien sans identifiant gardé', () => {
    const { tracker, http } = setup();
    tracker.resume();
    vi.advanceTimersByTime(2000);
    http.expectNone(() => true);
    expect(tracker.progress()).toBeNull();
  });

  it('abandonne un identifiant inconnu du serveur (404)', () => {
    localStorage.setItem(REPLAY_ID_KEY, 'old');
    const { tracker, http } = setup();
    tracker.resume();
    vi.advanceTimersByTime(0);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/old')
      .flush({ detail: 'inconnu' }, { status: 404, statusText: 'Not Found' });
    expect(localStorage.getItem(REPLAY_ID_KEY)).toBeNull();
    expect(tracker.progress()).toBeNull();
    vi.advanceTimersByTime(3000);
    http.expectNone(() => true);
  });

  it('affiche l’erreur d’un rejeu terminé en échec', () => {
    localStorage.setItem(REPLAY_ID_KEY, 'job-1');
    const { tracker, http } = setup();
    tracker.resume();
    vi.advanceTimersByTime(0);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/job-1')
      .flush(progress({ finished: true, error: 'Kafka injoignable' }));
    expect(tracker.progress()?.error).toBe('Kafka injoignable');
  });

  it('continue d’interroger après une erreur passagère', () => {
    localStorage.setItem(REPLAY_ID_KEY, 'job-1');
    const { tracker, http } = setup();
    tracker.resume();
    vi.advanceTimersByTime(0);
    http
      .expectOne('/api/admin/dlt/scan-events/replay/job-1')
      .flush(null, { status: 503, statusText: 'Unavailable' });
    vi.advanceTimersByTime(1000);
    http.expectOne('/api/admin/dlt/scan-events/replay/job-1').flush(progress({ replayed: 1 }));
    expect(tracker.progress()?.replayed).toBe(1);
    tracker.stop();
  });
});
