import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { EXPIRES_AT_KEY, LOGIN_URL, TOKEN_KEY } from '../../core/auth/auth.service';
import { fakeToken } from '../../core/auth/fake-token';
import { routes } from '../../app.routes';
import { Login } from './login';

function setup(returnUrl: string | null) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter(routes),
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: new Map(returnUrl ? [['returnUrl', returnUrl]] : []) } },
      },
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  const fixture = TestBed.createComponent(Login);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const submit = (accessToken: string) => {
    const component = fixture.componentInstance as unknown as {
      form: { setValue(v: { username: string; password: string }): void };
      submit(): void;
    };
    component.form.setValue({ username: 'u', password: 'p' });
    component.submit();
    http.expectOne(LOGIN_URL).flush({
      accessToken,
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    });
    fixture.detectChanges();
  };
  return { fixture, navigate, submit, el: fixture.nativeElement as HTMLElement };
}

describe('Login', () => {
  afterEach(() => localStorage.clear());

  it('suit le returnUrl quand le rôle du compte y a accès', () => {
    const { navigate, submit } = setup('/admin/users');
    submit(fakeToken({ sub: 'a', roles: ['ADMIN'] }));
    expect(navigate).toHaveBeenCalledWith('/admin/users');
  });

  it('ignore le returnUrl d’un autre rôle et va à la page d’arrivée', () => {
    const { navigate, submit } = setup('/admin/users');
    submit(fakeToken({ sub: 's', roles: ['SCANNER'], site: 'S1' }));
    expect(navigate).toHaveBeenCalledWith('/scan');
  });

  it('ignore le returnUrl du scan pour un OPERATOR', () => {
    const { navigate, submit } = setup('/scan');
    submit(fakeToken({ sub: 'o', roles: ['OPERATOR'] }));
    expect(navigate).toHaveBeenCalledWith('/shipments');
  });

  it('efface le jeton illisible, affiche une erreur et réactive le bouton', () => {
    const { fixture, navigate, submit, el } = setup(null);
    submit('pas-un-jeton');
    expect(navigate).not.toHaveBeenCalled();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(EXPIRES_AT_KEY)).toBeNull();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('illisible');
    // Le mot de passe est vidé après une erreur : une fois ressaisi, le bouton doit être actif.
    const password = el.querySelector<HTMLInputElement>('input[type=password]')!;
    password.value = 'p';
    password.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')?.disabled).toBe(false);
  });
});
