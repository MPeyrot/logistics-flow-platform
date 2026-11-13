import type { Role } from '../../../core/labels';

/** `UserResponse` de `GET /api/users`. */
export interface UserAccount {
  id: string;
  username: string;
  role: Role;
  site: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateUser {
  username: string;
  password: string;
  role: Role;
  site: string | null;
}

/** Longueurs du backend (`UserAccount`) : identifiant 3 à 100, mot de passe 12 au moins. */
export const USERNAME_MIN_LENGTH = 3;
export const PASSWORD_MIN_LENGTH = 12;

/** Le site est obligatoire pour un SCANNER et refusé (400) pour les autres rôles. */
export const siteRequired = (role: Role): boolean => role === 'SCANNER';
