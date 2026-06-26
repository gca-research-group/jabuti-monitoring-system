import { provideTranslateService, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { withNgxsStoragePlugin } from '@ngxs/storage-plugin';
import { provideStore } from '@ngxs/store';
import { LineChart, BarChart } from 'echarts/charts';
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { provideEchartsCore } from 'ngx-echarts';
import { provideToastr } from 'ngx-toastr';

import { DatePipe, registerLocaleData } from '@angular/common';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import localeEN from '@angular/common/locales/en';
import localeES from '@angular/common/locales/es';
import localePT from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideZoneChangeDetection,
} from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { requestInterceptor } from './interceptors';
import { CurrentUserState } from './state/current-user';
import { PreferencesState } from './state/preferences';

echarts.use([
  LineChart,
  BarChart,

  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
]);

registerLocaleData(localeES, 'es');
registerLocaleData(localePT, 'pt');
registerLocaleData(localeEN, 'en');

const httpLoaderFactory: (http: HttpClient) => TranslateHttpLoader = (
  http: HttpClient,
) => new TranslateHttpLoader(http, './i18n/', '.json');

export const appConfig: ApplicationConfig = {
  providers: [
    provideStore(
      [CurrentUserState, PreferencesState],
      withNgxsStoragePlugin({
        keys: '*',
      }),
    ),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    provideToastr({
      closeButton: true,
      progressBar: true,
    }),
    provideHttpClient(withInterceptors([requestInterceptor])),
    provideTranslateService({
      loader: {
        provide: TranslateLoader,
        useFactory: httpLoaderFactory,
        deps: [HttpClient],
      },
      defaultLanguage: 'en',
    }),
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: { appearance: 'outline', subscriptSizing: 'dynamic' },
    },
    [{ provide: LOCALE_ID, useFactory: () => navigator.language }],
    DatePipe,
    provideEchartsCore({ echarts }),
  ],
};
