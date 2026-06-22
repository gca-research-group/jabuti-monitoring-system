import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import {
  Component,
  inject,
  OnDestroy,
  signal,
  effect,
  OnInit,
  computed,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink } from '@angular/router';

import { Breadcrumb } from '@app/models';
import { BreadcrumbService } from '@app/services/breadcrumb';
import { CurrentUserService } from '@app/services/current-user';
import { LanguageService } from '@app/services/language';
import { SidebarService } from '@app/services/sidebar';

import { BreadcrumbComponent } from '../breadcrumb';
import { IconComponent } from '../icon';
import { IconButtonComponent } from '../icon-button';
import { LanguageSelectorComponent } from '../language-selector/language-selector.component';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrl: './toolbar.component.scss',
  imports: [
    RouterLink,

    TranslateModule,

    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatToolbar,

    BreadcrumbComponent,
    IconButtonComponent,
    IconComponent,
    LanguageSelectorComponent,
  ],
})
export class ToolbarComponent implements OnInit, OnDestroy {
  private sidebarService = inject(SidebarService);
  private translateService = inject(TranslateService);
  private languageService = inject(LanguageService);
  private currentUserService = inject(CurrentUserService);
  private router = inject(Router);

  currentUser = this.currentUserService.currentUser$;

  currentUserName = computed(() => this.currentUser()?.name);

  isCollapsed = true;
  language = signal(this.languageService.language);

  private breadcrumbService = inject(BreadcrumbService);
  breadcrumb: Breadcrumb[] = [];

  private onDestroy$ = new Subject();

  constructor() {
    this.breadcrumbService.breadcrumb$
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(breadcrumb => {
        this.breadcrumb = breadcrumb;
      });

    effect(() => {
      this.translateService.use(this.language());
      this.languageService.update(this.language());
    });
  }

  ngOnInit(): void {
    this.sidebarService.isCollapsed$
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(value => {
        this.isCollapsed = value;
      });
  }

  ngOnDestroy(): void {
    this.onDestroy$.complete();
    this.onDestroy$.unsubscribe();
  }

  toggleSadebar() {
    this.sidebarService.toggleSidebar();
  }

  logout() {
    this.currentUserService.remove();
    void this.router.navigate(['/login']);
  }
}
