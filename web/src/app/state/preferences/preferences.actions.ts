export class FixMenuAction {
  static readonly type = '[Preferences] Fix the menu';
  constructor(readonly isMenuFixed: boolean) {}
}
