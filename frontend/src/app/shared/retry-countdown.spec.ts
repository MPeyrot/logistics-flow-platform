import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RetryCountdown } from './retry-countdown';

@Component({
  imports: [RetryCountdown],
  template: `<app-retry-countdown [seconds]="seconds()" (finished)="done = done + 1" />`,
})
class Host {
  readonly seconds = signal(3);
  done = 0;
}

describe('RetryCountdown', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  async function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const text = () => (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
    const tick = async (ms: number) => {
      vi.advanceTimersByTime(ms);
      fixture.detectChanges();
    };
    return { fixture, text, tick };
  }

  it('décompte chaque seconde puis signale la fin', async () => {
    const { fixture, text, tick } = await render();
    expect(text()).toBe('Réessayez dans 3 s');
    await tick(1000);
    expect(text()).toBe('Réessayez dans 2 s');
    await tick(1000);
    expect(fixture.componentInstance.done).toBe(0);
    await tick(1000);
    expect(text()).toBe('');
    expect(fixture.componentInstance.done).toBe(1);
    await tick(5000);
    expect(fixture.componentInstance.done).toBe(1);
  });

  it('repart de zéro quand les secondes changent', async () => {
    const { fixture, text, tick } = await render();
    await tick(2000);
    fixture.componentInstance.seconds.set(10);
    fixture.detectChanges();
    expect(text()).toBe('Réessayez dans 10 s');
  });
});
