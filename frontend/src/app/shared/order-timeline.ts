import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderDetail } from '../core/models';

interface Step {
  label: string;
  at?: string;
  done: boolean;
}

/** Fulfillment progress: placed → paid → shipped → delivered. */
@Component({
  selector: 'jf-order-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    @if (order().status === 'CANCELLED') {
      <div class="alert error">
        Cancelled {{ order().cancelledAt | date: 'medium' }} — the payment did not complete and reserved items were released.
      </div>
    } @else if (order().status === 'PAYMENT_REVIEW') {
      <div class="alert warning">Your payment is being reviewed by our team before fulfillment.</div>
    } @else {
      <ol class="timeline">
        @for (step of steps(); track step.label) {
          <li [class.done]="step.done">
            <span class="dot" aria-hidden="true"></span>
            <div>
              <strong>{{ step.label }}</strong>
              @if (step.at) {
                <div class="muted small">{{ step.at | date: 'medium' }}</div>
              }
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
    }
    li {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      color: var(--muted);
    }
    li.done {
      color: var(--jf-navy);
    }
    .dot {
      width: 14px;
      height: 14px;
      border-radius: 50%;
      border: 3px solid var(--border);
      margin-top: 4px;
      flex-shrink: 0;
    }
    li.done .dot {
      border-color: var(--jf-blue);
      background: var(--jf-blue);
    }
    @media (max-width: 640px) {
      .timeline {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class OrderTimeline {
  readonly order = input.required<OrderDetail>();

  protected readonly steps = computed<Step[]>(() => {
    const o = this.order();
    return [
      { label: 'Order placed', at: o.createdAt, done: true },
      { label: 'Payment confirmed', at: o.paidAt, done: !!o.paidAt },
      { label: 'Shipped', at: o.shippedAt, done: !!o.shippedAt },
      { label: 'Delivered', at: o.deliveredAt, done: !!o.deliveredAt },
    ];
  });
}
