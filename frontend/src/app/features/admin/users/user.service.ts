import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HANDLES_ERRORS } from '../../../core/http/interceptors';
import type { CreateUser, UserAccount } from './user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  list(): Observable<UserAccount[]> {
    return this.http.get<UserAccount[]>('/api/users');
  }

  /** Les erreurs sont affichées sous le formulaire de création. */
  create(user: CreateUser): Observable<UserAccount> {
    return this.http.post<UserAccount>('/api/users', user, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }

  setActive(id: string, active: boolean): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`/api/users/${encodeURIComponent(id)}`, { active });
  }
}
