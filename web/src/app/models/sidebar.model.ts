export interface Sidebar {
  label: string;
  icon: string;
  url?: string;
  roles?: string[];
  children?: {
    label: string;
    icon: string;
    url: string;
    roles: string[];
  }[];
}
