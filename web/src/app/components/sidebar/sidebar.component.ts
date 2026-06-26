import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { NgClass } from '@angular/common';
import {
  Component,
  computed,
  HostListener,
  inject,
  input,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';

import { IconComponent } from '@app/components/icon';
import { Sidebar } from '@app/models';
import { PreferencesService } from '@app/services/preferences';
import { SidebarService } from '@app/services/sidebar';

import { IconButtonComponent } from '../icon-button';

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  imports: [
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatExpansionModule,
    TranslateModule,
    IconComponent,
    NgClass,
    IconButtonComponent,
  ],
})
export class SidebarComponent implements OnInit, OnDestroy {
  items = input<Sidebar[]>([]);

  private sidebarService = inject(SidebarService);
  private preferencesService = inject(PreferencesService);
  private preferences$ = this.preferencesService.preferences$;
  isMenuFixed = computed(() => this.preferences$()?.isMenuFixed);

  private onDestroy$ = new Subject();

  private router = inject(Router);
  private title = inject(Title);
  private translateService = inject(TranslateService);

  isCollapsed = true;
  opened = false;

  currentIndex: number | null = null;
  currentUrl?: string;
  currentTitle?: string;

  @HostListener('document:click', ['$event.target'])
  public onClick(targetElement: EventTarget | null) {
    if (!(targetElement instanceof HTMLElement)) {
      return;
    }

    const navbar = document.querySelector('.sidebar');

    const isCloseButton =
      targetElement.tagName.toLowerCase() === 'mat-icon' &&
      (targetElement.getAttribute('aria-label') === 'close' ||
        targetElement.textContent === 'menu');

    const clickedInside = navbar?.contains(targetElement) || isCloseButton;

    if (!clickedInside && !this.isCollapsed && !this.isMenuFixed()) {
      this.sidebarService.collapse();
      this.opened = false;
    }
  }

  constructor() {
    this.translateService.onLangChange
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(_ => {
        if (this.currentTitle) {
          this.translateService.get(this.currentTitle).subscribe(translated => {
            this.title.setTitle(`JMS | ${translated}`);
          });
        }
      });

    this.sidebarService.isCollapsed$
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(value => {
        this.isCollapsed = value;
        if (this.isCollapsed) {
          this.opened = false;
        }
      });
  }

  ngOnInit(): void {
    this.setUp();
  }

  ngOnDestroy(): void {
    this.onDestroy$.unsubscribe();
  }

  setUp() {
    this.currentUrl = this.router.url.split("?").at(0);
    if (this.currentUrl?.startsWith('/')) {
      this.currentUrl = this.currentUrl.replace('/', '')
    }

    this.items().forEach((item, index) => {
      if (item.children) {
        const _item = item.children.find(
          child => child.url === this.currentUrl,
        );

        if (_item) {
          this.currentIndex = index;
          this.setTitle(_item.label);
        }
      } else if (item.url === this.currentUrl) {
        this.currentIndex = index;
        this.setTitle(item.label);
      }
    });
  }

  open(index: number) {
    this.opened = this.currentIndex === index ? !this.opened : true;
    this.currentIndex = index;
    this.sidebarService.open();
  }

  navigate(item: Sidebar) {
    this.sidebarService.collapse();
    this.setTitle(item.label);
    void this.router.navigate([item.url]);
  }

  setCurrentIndexAndCurrentUrl(item: Sidebar, index: number) {
    this.currentIndex = index;
    this.currentUrl = item.url!;
  }

  setTitle(title: string) {
    this.currentTitle = title;
    this.translateService
      .get(title)
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(translated => {
        this.title.setTitle(`JMS | ${translated}`);
      });
  }

  toggleIsFixed() {
    this.preferencesService.toggleFixedMenu();
  }
}
