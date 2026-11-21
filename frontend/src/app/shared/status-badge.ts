import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus, PaymentStatus } from '../core/models';

const ORDER_LABELS: Record<OrderStatus, [string, string]> = {
  PENDING_PAYMENT: ['Awaiting payment', 'amber'],
  PAID: ['Paid', 'blue'],
  SHIPPED: ['Shipped', 'navy'],
  DELIVERED: ['Delivered', 'green'],
  CANCELLED: ['Cancelled', 'red'],
  PAYMENT_REVIEW: ['Payment review', 'amber'],
};

const PAYMENT_LABELS: Record<PaymentStatus, [string, string]> = {
  CREATED: ['Not started', ''],
  PENDING: ['Pending', 'amber'],
  SUCCEEDED: ['Succeeded', 'green'],
  FAILED: ['Failed', 'red'],
  EXPIRED: ['Expired', 'red'],
};

@Component({
  selector: 'jf-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge {{ tone() }}">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<OrderStatus | PaymentStatus | undefined | null>();
  readonly kind = input<'order' | 'payment'>('order');

  private readonly entry = computed<[string, string]>(() => {
    const s = this.status();
    if (!s) return ['—', ''];
    const table = (this.kind() === 'order' ? ORDER_LABELS : PAYMENT_LABELS) as Record<string, [string, string]>;
    return table[s] ?? [s, ''];
  });
  protected readonly label = computed(() => this.entry()[0]);
  protected readonly tone = computed(() => this.entry()[1]);
}
