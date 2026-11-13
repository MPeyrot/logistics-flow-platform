import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { AuthService } from '../../../core/auth/auth.service';
import { ROLE_LABELS } from '../../../core/labels';
import { formatDateTime } from '../../shipments/format-date';
import { ConfirmDialog, type ConfirmData } from '../confirm-dialog';
import { CreateUserDialog } from './create-user-dialog';
import type { UserAccount } from './user.model';
import { UserService } from './user.service';

@Component({
  selector: 'app-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButton, MatSlideToggle],
  styleUrl: './users.scss',
  templateUrl: './users.html',
})
export class Users {
  private readonly service = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly formatDate = formatDateTime;
  protected readonly users = signal<UserAccount[]>([]);
  protected readonly loaded = signal(false);
  protected readonly loading = signal(false);

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.users.set(list);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /** Le compte connecté ne peut pas se désactiver lui-même : pas d'interrupteur sur sa ligne. */
  protected isSelf(user: UserAccount): boolean {
    return user.username === this.auth.session()?.username;
  }

  protected openCreate(): void {
    this.dialog
      .open<CreateUserDialog, void, boolean>(CreateUserDialog)
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.refresh();
        }
      });
  }

  /** Désactiver demande confirmation ; réactiver non. */
  protected toggle(user: UserAccount, toggle?: MatSlideToggle): void {
    if (!user.active) {
      this.setActive(user, true, toggle);
      return;
    }
    this.dialog
      .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
        data: {
          title: 'Désactiver le compte',
          message: `Désactiver « ${user.username} » ? Son jeton actuel reste valable jusqu’à son expiration.`,
          confirm: 'Désactiver',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.setActive(user, false, toggle);
        } else if (toggle) {
          toggle.checked = user.active;
        }
      });
  }

  private setActive(user: UserAccount, active: boolean, toggle?: MatSlideToggle): void {
    this.service.setActive(user.id, active).subscribe({
      next: (updated) =>
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u))),
      // L'interrupteur revient à l'état du serveur ; le message commun s'affiche.
      error: () => {
        if (toggle) {
          toggle.checked = user.active;
        }
      },
    });
  }
}
