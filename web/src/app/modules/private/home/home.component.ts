import { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  imports: [NgxEchartsDirective],
})
export class HomeComponent implements OnInit {
  options: EChartsCoreOption = {
    title: { text: 'Example Chart' },
    tooltip: {},
    xAxis: { data: ['A', 'B', 'C'] },
    yAxis: {},
    series: [
      {
        type: 'line',
        data: [5, 20, 36],
      },
    ],
  };

  ngOnInit(): void {
    // Initialize or fetch data for the chart here if needed
    console.log('HomeComponent initialized with options:', this.options);
  }
}
