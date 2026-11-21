import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CatalogApi } from './api';
import { StoreInfo } from './models';

@Injectable({ providedIn: 'root' })
export class StoreInfoService {
  private readonly api = inject(CatalogApi);

  readonly info = signal<StoreInfo>({ currency: 'USD', shippingCountry: 'US', flatShippingFee: 0 });

  async load(): Promise<void> {
    try {
      this.info.set(await firstValueFrom(this.api.storeInfo()));
    } catch {
      // Keep defaults; pages still render with server-provided currencies.
    }
  }
}
