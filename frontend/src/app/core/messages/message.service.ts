import { Injectable, signal } from '@angular/core';

/** Message d'erreur commun aux écrans : un seul affiché à la fois, le dernier remplace le précédent. */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly state = signal<string | null>(null);

  readonly current = this.state.asReadonly();

  error(text: string): void {
    this.state.set(text);
  }

  clear(): void {
    this.state.set(null);
  }
}
