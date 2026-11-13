import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { describeError } from '../../../core/http/error-messages';
import { ROLE_LABELS, type Role } from '../../../core/labels';
import { PASSWORD_MIN_LENGTH, USERNAME_MIN_LENGTH, siteRequired } from './user.model';
import { UserService } from './user.service';

/** Site obligatoire (non vide) pour un SCANNER ; confirmation égale au mot de passe. */
function groupRules(group: AbstractControl): ValidationErrors | null {
  const { role, site, password, confirmation } = group.value as {
    role: Role;
    site: string;
    password: string;
    confirmation: string;
  };
  const errors: ValidationErrors = {};
  if (siteRequired(role) && site.trim() === '') {
    errors['siteRequired'] = true;
  }
  if (password !== confirmation) {
    errors['mismatch'] = true;
  }
  return Object.keys(errors).length > 0 ? errors : null;
}

@Component({
  selector: 'app-create-user-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatFormField,
    MatLabel,
    MatHint,
    MatInput,
    MatButton,
  ],
  template: `
    <h2 mat-dialog-title>Créer un compte</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="fields">
        <mat-form-field>
          <mat-label>Identifiant</mat-label>
          <input matInput formControlName="username" maxlength="100" autocomplete="off" />
          <mat-hint>{{ usernameMin }} caractères au moins</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Mot de passe</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password" />
          <mat-hint>{{ passwordMin }} caractères au moins</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Confirmation du mot de passe</mat-label>
          <input
            matInput
            type="password"
            formControlName="confirmation"
            autocomplete="new-password"
          />
          @if (form.hasError('mismatch') && form.controls.confirmation.dirty) {
            <mat-hint class="error">Les mots de passe diffèrent.</mat-hint>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Rôle</mat-label>
          <select matNativeControl formControlName="role">
            @for (r of roles; track r) {
              <option [value]="r">{{ roleLabels[r] }}</option>
            }
          </select>
        </mat-form-field>
        @if (needsSite()) {
          <mat-form-field>
            <mat-label>Site</mat-label>
            <input matInput formControlName="site" maxlength="100" />
          </mat-form-field>
        }
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" mat-dialog-close>Annuler</button>
        <button matButton="filled" type="submit" [disabled]="form.invalid || sending()">
          Créer
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .fields {
      display: flex;
      flex-direction: column;
      min-width: min(24rem, 80vw);
    }
    .error {
      color: var(--mat-sys-error);
      margin: 0;
    }
  `,
})
export class CreateUserDialog {
  private readonly service = inject(UserService);
  private readonly dialogRef = inject<MatDialogRef<CreateUserDialog, boolean>>(MatDialogRef);

  protected readonly roles = Object.keys(ROLE_LABELS) as Role[];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly usernameMin = USERNAME_MIN_LENGTH;
  protected readonly passwordMin = PASSWORD_MIN_LENGTH;

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      username: ['', [Validators.required, Validators.minLength(USERNAME_MIN_LENGTH)]],
      password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]],
      confirmation: [''],
      role: ['OPERATOR' as Role],
      site: [''],
    },
    { validators: groupRules },
  );
  private readonly role = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });
  protected readonly needsSite = () => siteRequired(this.role());
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.service
      .create({
        username: value.username.trim(),
        password: value.password,
        role: value.role,
        site: siteRequired(value.role) ? value.site.trim() : null,
      })
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: (err: unknown) => {
          this.sending.set(false);
          this.error.set(
            err instanceof HttpErrorResponse ? describeError(err) : 'Une erreur est survenue.',
          );
        },
      });
  }
}
