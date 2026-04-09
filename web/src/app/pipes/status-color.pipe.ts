import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'statusColor' })
export class StatusColorPipe implements PipeTransform {
  transform(key: string) {
    switch (key) {
      case 'PENDING':
        return 'amber';
      case 'PROCESSING':
        return 'purple';
      case 'SUCCESS':
        return 'green';
      case 'QUEUED':
        return 'blue';
      default:
        return 'red';
    }
  }
}
