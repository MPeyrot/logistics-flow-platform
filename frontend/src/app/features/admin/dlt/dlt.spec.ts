import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { prettyJson, shortClassName, type DeadLetter } from './dlt.model';
import { Dlt } from './dlt';
import { REPLAY_ID_KEY } from './replay-tracker';

const letter = (over: Partial<DeadLetter>): DeadLetter => ({
  partition: 0,
  offset: 1,
  timestamp: '2026-11-13T10:00:00Z',
  key: 'REF-1',
  value: '{"a":1}',
  exceptionClass: 'org.example.InvalidScanException',
  exceptionMessage: 'Transition interdite',
  replayed: false,
  replayedAt: null,
  ...over,
});

function setup() {
  const open = vi.fn(() => ({ afterClosed: () => of(true) }));
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialog, useValue: { open } },
    ],
  });
  const fixture = TestBed.createComponent(Dlt);
  fixture.detectChanges();
  return {
    fixture,
    http: TestBed.inject(HttpTestingController),
    el: fixture.nativeElement as HTMLElement,
  };
}

const LIST = '/api/admin/dlt/scan-events';

describe('formatage du DLT', () => {
  it('garde le nom court de la classe', () => {
    expect(shortClassName('org.example.InvalidScanException')).toBe('InvalidScanException');
    expect(shortClassName('Plain')).toBe('Plain');
    expect(shortClassName(null)).toBe('—');
  });

  it('met le JSON en forme et laisse le texte brut', () => {
    expect(prettyJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(prettyJson('pas du json')).toBe('pas du json');
  });
});

describe('Dlt', () => {
  beforeEach(() => localStorage.clear());

  it('liste les messages avec classe courte et dépliant JSON', () => {
    const { fixture, http, el } = setup();
    http.expectOne(LIST).flush([letter({})]);
    fixture.detectChanges();
    expect(el.querySelector('tbody tr')?.textContent).toContain('InvalidScanException');
    expect(el.querySelector('pre')).toBeNull();
    (el.querySelector('button[aria-expanded]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('pre')?.textContent).toBe('{\n  "a": 1\n}');
  });

  it('affiche « Déjà rejoué » sur un 409', () => {
    const { fixture, http, el } = setup();
    http.expectOne(LIST).flush([letter({})]);
    fixture.detectChanges();
    (fixture.componentInstance as unknown as { replay(l: DeadLetter): void }).replay(letter({}));
    http
      .expectOne({ method: 'POST', url: `${LIST}/0/1/replay` })
      .flush({ detail: 'déjà' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el.querySelector('tbody tr')?.textContent).toContain('Déjà rejoué');
  });

  it('reprend le suivi après rechargement et rafraîchit la liste à la fin', () => {
    vi.useFakeTimers();
    try {
      localStorage.setItem(REPLAY_ID_KEY, 'job-1');
      const { fixture, http, el } = setup();
      http.expectOne(LIST).flush([letter({})]);
      vi.advanceTimersByTime(0);
      http
        .expectOne(`${LIST}/replay/job-1`)
        .flush({ id: 'job-1', finished: false, replayed: 4, published: 2, failed: 0, error: null });
      fixture.detectChanges();
      expect(el.querySelector('mat-progress-bar')).not.toBeNull();
      expect(el.querySelector('.progress')?.textContent).toContain('4 rejoués');

      vi.advanceTimersByTime(1000);
      http
        .expectOne(`${LIST}/replay/job-1`)
        .flush({ id: 'job-1', finished: true, replayed: 4, published: 4, failed: 0, error: null });
      fixture.detectChanges();
      http.expectOne(LIST).flush([letter({ replayed: true, replayedAt: '2026-11-13T10:05:00Z' })]);
      fixture.detectChanges();
      expect(el.querySelector('.progress')?.textContent).toContain('Terminé');
      expect(localStorage.getItem(REPLAY_ID_KEY)).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
