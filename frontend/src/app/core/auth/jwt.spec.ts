import { fakeToken } from './fake-token';
import { decodeToken } from './jwt';

describe('decodeToken', () => {
  it('lit sub, roles et site', () => {
    expect(decodeToken(fakeToken({ sub: 'alice', roles: ['SCANNER'], site: 'PAR-01' }))).toEqual({
      username: 'alice',
      roles: ['SCANNER'],
      site: 'PAR-01',
    });
  });

  it('rend site null pour un compte sans site', () => {
    expect(decodeToken(fakeToken({ sub: 'bob', roles: ['ADMIN'] }))?.site).toBeNull();
  });

  it('décode l’UTF-8 et le base64url', () => {
    expect(decodeToken(fakeToken({ sub: 'zoé~?>', roles: ['OPERATOR'] }))?.username).toBe('zoé~?>');
  });

  it('ignore un rôle inconnu', () => {
    expect(decodeToken(fakeToken({ sub: 'a', roles: ['ROOT', 'ADMIN'] }))?.roles).toEqual(['ADMIN']);
  });

  it.each(['', 'abc', 'a.b.c', 'e30.bm90LWpzb24.sig'])('rend null pour %j', (token) => {
    expect(decodeToken(token)).toBeNull();
  });

  it('rend null sans sub ni roles', () => {
    expect(decodeToken(fakeToken({ roles: ['ADMIN'] }))).toBeNull();
    expect(decodeToken(fakeToken({ sub: 'a' }))).toBeNull();
  });
});
