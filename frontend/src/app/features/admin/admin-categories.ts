import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { Category } from '../../core/models';

@Component({
  selector: 'jf-admin-categories',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <h1>Categories</h1>
    @if (message(); as m) {
      <div class="alert" [class.error]="!m.ok" [class.success]="m.ok">{{ m.text }}</div>
    }
    <div class="card stack">
      <form class="row add" (ngSubmit)="create()">
        <input name="name" placeholder="New category name" [(ngModel)]="newName" maxlength="80" />
        <button class="btn" type="submit" [disabled]="!newName.trim()">Add category</button>
      </form>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Name</th><th>Slug</th><th></th></tr></thead>
          <tbody>
            @for (c of categories.value() ?? []; track c.id) {
              <tr>
                <td>
                  @if (editingId() === c.id) {
                    <input [(ngModel)]="editName" name="edit-{{ c.id }}" maxlength="80" />
                  } @else {
                    {{ c.name }}
                  }
                </td>
                <td class="muted">{{ c.slug }}</td>
                <td class="right">
                  @if (editingId() === c.id) {
                    <button class="btn small" (click)="rename(c)">Save</button>
                    <button class="btn secondary small" (click)="editingId.set(null)">Cancel</button>
                  } @else {
                    <button class="btn secondary small" (click)="startEdit(c)">Rename</button>
                    <button class="btn danger small" (click)="remove(c)">Delete</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .add {
      flex-wrap: nowrap;
    }
    .add input {
      max-width: 360px;
    }
    td .btn + .btn {
      margin-left: 6px;
    }
  `,
})
export class AdminCategories {
  private readonly api = inject(AdminApi);

  protected readonly categories = rxResource({ stream: () => this.api.categories() });
  protected readonly editingId = signal<number | null>(null);
  protected readonly message = signal<{ text: string; ok: boolean } | null>(null);
  protected newName = '';
  protected editName = '';

  protected startEdit(category: Category): void {
    this.editingId.set(category.id);
    this.editName = category.name;
  }

  protected create(): Promise<void> {
    const name = this.newName.trim();
    return this.run(() => firstValueFrom(this.api.createCategory(name)), `Category “${name}” added.`, () => (this.newName = ''));
  }

  protected rename(category: Category): Promise<void> {
    return this.run(
      () => firstValueFrom(this.api.renameCategory(category.id, this.editName.trim())),
      'Category renamed.',
      () => this.editingId.set(null),
    );
  }

  protected remove(category: Category): Promise<void> {
    if (!confirm(`Delete category “${category.name}”?`)) return Promise.resolve();
    return this.run(() => firstValueFrom(this.api.deleteCategory(category.id)), 'Category deleted.');
  }

  private async run(action: () => Promise<unknown>, success: string, after?: () => void): Promise<void> {
    this.message.set(null);
    try {
      await action();
      after?.();
      this.message.set({ text: success, ok: true });
      this.categories.reload();
    } catch (e) {
      this.message.set({ text: errorMessage(e), ok: false });
    }
  }
}
