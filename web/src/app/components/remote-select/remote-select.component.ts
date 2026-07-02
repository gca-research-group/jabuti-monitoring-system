import { TranslateModule } from '@ngx-translate/core';
import {
  debounceTime,
  distinctUntilChanged,
  filter,
  fromEvent,
  Subscription,
} from 'rxjs';

import { NgTemplateOutlet } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  TemplateRef,
  ViewChild,
  inject,
  input,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelect, MatSelectModule } from '@angular/material/select';

import { CustomControlValueAccessorDirective } from '@app/directives/custom-control-value-accessor';

type RemoteSelectItem = { id: string | number; [key: string]: unknown };

@Component({
  selector: 'app-remote-select',
  templateUrl: './remote-select.component.html',
  styleUrl: './remote-select.component.scss',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    NgTemplateOutlet,
    ReactiveFormsModule,
    TranslateModule,
  ],
})
export class RemoteSelectComponent
  extends CustomControlValueAccessorDirective
  implements OnDestroy
{
  private destroyRef = inject(DestroyRef);
  private scrollSubscription?: Subscription;

  label = input<string>();
  items = input<RemoteSelectItem[]>([]);
  key = input('name');
  loading = input(false);
  hasMore = input(false);
  canCreate = input(false);
  createLabel = input('create');
  clearable = input(true);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  template = input<TemplateRef<any> | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  createTemplate = input<TemplateRef<any> | null>(null);

  searchChange = output<string>();
  loadMore = output<void>();
  create = output<string>();

  searchControl = new FormControl('', { nonNullable: true });

  @ViewChild(MatSelect) matSelect?: MatSelect;
  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  constructor() {
    super();

    this.searchControl.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(value => this.searchChange.emit(value));
  }

  ngOnDestroy(): void {
    this.scrollSubscription?.unsubscribe();
  }

  openedChange(opened: boolean) {
    if (!opened) {
      this.scrollSubscription?.unsubscribe();
      return;
    }

    setTimeout(() => {
      this.searchInput?.nativeElement.focus();
      this.watchPanelScroll();
    });
  }

  stopPanelInteraction(event: Event) {
    event.stopPropagation();
  }

  clearSearch(event: Event) {
    event.stopPropagation();
    this.searchControl.setValue('');
  }

  requestCreate(event: Event) {
    event.stopPropagation();
    this.emitCreate();
  }

  emitCreate(search = this.searchControl.value) {
    const term = search.trim();
    if (term) {
      this.create.emit(term);
    }
  }

  getCreateTemplateContext() {
    const term = this.searchControl.value.trim();
    return {
      $implicit: term,
      search: term,
      create: (value = term) => this.emitCreate(value),
    };
  }

  getItemLabel(item: RemoteSelectItem) {
    const value = item[this.key()];

    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }

    return '';
  }

  isSelected(value: string | number) {
    return String(this.formControl.value ?? '') === String(value);
  }

  private watchPanelScroll() {
    this.scrollSubscription?.unsubscribe();

    const panel = this.matSelect?.panel?.nativeElement as
      | HTMLElement
      | undefined;
    if (!panel) return;

    this.scrollSubscription = fromEvent(panel, 'scroll')
      .pipe(filter(() => this.isNearPanelEnd(panel)))
      .subscribe(() => this.loadMore.emit());
  }

  private isNearPanelEnd(panel: HTMLElement) {
    return (
      this.hasMore() &&
      !this.loading() &&
      panel.scrollTop + panel.clientHeight >= panel.scrollHeight - 80
    );
  }
}
