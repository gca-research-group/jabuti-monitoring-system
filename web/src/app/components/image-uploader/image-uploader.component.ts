import { TranslateModule } from '@ngx-translate/core';
import { v4 as uuidv4 } from 'uuid';

import { Component, forwardRef, input, output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { IconButtonComponent } from '../icon-button';

@Component({
  selector: 'app-image-uploader',
  templateUrl: './image-uploader.component.html',
  styleUrl: './image-uploader.component.scss',
  host: {
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.minHeight]': 'minHeight()',
    '[style.borderRadius]': 'borderRadius()',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ImageUploaderComponent),
      multi: true,
    },
  ],
  imports: [IconButtonComponent, TranslateModule],
})
export class ImageUploaderComponent implements ControlValueAccessor {
  readonly inputId = `image-uploader-${uuidv4()}`;

  fileSelected = output<File>();
  file?: string;

  description = input<string>();
  showIcon = input(true);

  width = input<string>('196px');
  height = input<string>('196px');
  minHeight = input<string>();
  borderRadius = input<string>('50%');

  onChange: (file: string) => void = () => {};
  onTouched: () => void = () => {};

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    (event.currentTarget as HTMLElement).classList.add('dragover');
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    (event.currentTarget as HTMLElement).classList.remove('dragover');
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    (event.currentTarget as HTMLElement).classList.remove('dragover');

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.handleFile(event.dataTransfer.files[0]);
    }
  }

  private handleFile(file: File): void {
    const fileReader = new FileReader();

    fileReader.onload = () => {
      this.file = fileReader.result as string;
      this.fileSelected.emit(file);
      this.onChange(this.file);
      this.onTouched();
    };

    fileReader.readAsDataURL(file);
  }

  writeValue(file: string): void {
    this.file = file;
  }

  registerOnChange(fn: (file: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
}
