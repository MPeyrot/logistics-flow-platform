import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_LABELS, Role } from '../../core/labels';

interface MenuItem {
  label: string;
  path: string;
}

const STAFF_MENU: MenuItem[] = [
  { label: 'Expéditions', path: '/shipments' },
  { label: 'Anomalies', path: '/anomalies' },
  { label: 'Bons de livraison', path: '/delivery-notes' },
];
const ADMIN_MENU: MenuItem[] = [
  { label: 'Comptes', path: '/admin/users' },
  { label: 'Messages en échec', path: '/admin/dlt' },
];
const SCANNER_MENU: MenuItem[] = [
  { label: 'Scan', path: '/scan' },
  { label: 'Import CSV', path: '/scan/import' },
];

/** Menu d'un compte selon ses rôles. */
export function menuFor(roles: Role[]): MenuItem[] {
  const menu: MenuItem[] = [];
  if (roles.includes('ADMIN') || roles.includes('OPERATOR')) {
    menu.push(...STAFF_MENU);
  }
  if (roles.includes('ADMIN')) {
    menu.push(...ADMIN_MENU);
  }
  if (roles.includes('SCANNER')) {
    menu.push(...SCANNER_MENU);
  }
  return menu;
}

@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButton, RouterLink, RouterLinkActive],
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header {
  protected readonly auth = inject(AuthService);
  protected readonly menu = computed(() => menuFor(this.auth.session()?.roles ?? []));
  protected readonly roleLabel = computed(() =>
    (this.auth.session()?.roles ?? []).map((r) => ROLE_LABELS[r]).join(', '),
  );
}
