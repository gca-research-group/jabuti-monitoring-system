import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SidebarComponent } from '@app/components/sidebar';
import { ToolbarComponent } from '@app/components/toolbar';
import { Sidebar } from '@app/models';

@Component({
  selector: 'app-wrapper',
  templateUrl: './wrapper.component.html',
  styleUrl: './wrapper.component.scss',
  imports: [RouterOutlet, ToolbarComponent, SidebarComponent],
})
export class WrapperComponent {
  menus: Sidebar[] = [
    { label: 'home', icon: 'home', url: '' },
    {
        label: 'users',
        icon: 'group',
        url: 'user',
    },
    { label: 'blockchain-connection', icon: 'link', url: 'blockchain' },
    { label: 'smart-contract', icon: 'article', url: 'smart-contract' },
    {
      label: 'smart-contract-execution',
      icon: 'settings',
      url: 'smart-contract-execution',
    },
  ];
}
