import { Role } from '../labels';

const ROLES: readonly Role[] = ['ADMIN', 'OPERATOR', 'SCANNER'];

export interface TokenClaims {
  username: string;
  roles: Role[];
  site: string | null;
}

/**
 * Lit la charge d'un JWT SANS vérifier sa signature : pour l'affichage et la navigation
 * seulement, le serveur reste l'autorité. Rend null si le texte n'est pas un JWT lisible.
 */
export function decodeToken(token: string): TokenClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (typeof payload !== 'object' || payload === null) {
      return null;
    }
    const { sub, roles, site } = payload as Record<string, unknown>;
    if (typeof sub !== 'string' || !Array.isArray(roles)) {
      return null;
    }
    return {
      username: sub,
      roles: roles.filter((r): r is Role => ROLES.includes(r as Role)),
      site: typeof site === 'string' ? site : null,
    };
  } catch {
    return null;
  }
}
