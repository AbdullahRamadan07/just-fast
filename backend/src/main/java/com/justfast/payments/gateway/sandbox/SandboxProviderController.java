package com.justfast.payments.gateway.sandbox;

import com.justfast.payments.gateway.PaymentGateway.SessionState;
import com.justfast.payments.gateway.sandbox.SandboxPaymentGateway.SandboxSession;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The sandbox provider's "hosted checkout" page. It plays the role of an external payment site: the customer is
 * redirected here, chooses an outcome, and is sent back to the store's return URL. No card data is collected.
 */
@RestController
@RequestMapping("/api/sandbox-provider/checkout")
@ConditionalOnProperty(prefix = "payments", name = "provider", havingValue = "sandbox", matchIfMissing = true)
public class SandboxProviderController {

    private final SandboxPaymentGateway gateway;

    public SandboxProviderController(SandboxPaymentGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping(value = "/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String sessionId) {
        return gateway.find(sessionId)
                .map(session -> ResponseEntity.ok(render(session)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(shell("<h1>Session not found</h1>")));
    }

    @PostMapping("/{sessionId}/pay")
    public ResponseEntity<Void> pay(@PathVariable String sessionId) {
        return finish(sessionId, SessionState.PAID);
    }

    @PostMapping("/{sessionId}/decline")
    public ResponseEntity<Void> decline(@PathVariable String sessionId) {
        return finish(sessionId, SessionState.FAILED);
    }

    private ResponseEntity<Void> finish(String sessionId, SessionState outcome) {
        return gateway.complete(sessionId, outcome)
                .map(session -> ResponseEntity.status(HttpStatus.SEE_OTHER)
                        .location(URI.create(session.request().successUrl())).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String render(SandboxSession session) {
        var r = session.request();
        String id = HtmlUtils.htmlEscape(session.id());
        String amount = HtmlUtils.htmlEscape(r.amount().toPlainString() + " " + r.currency());
        String order = HtmlUtils.htmlEscape(r.orderNumber());
        if (session.state() != SessionState.OPEN) {
            return shell("""
                    <h1>Session %s</h1>
                    <p>This checkout session is <strong>%s</strong>.</p>
                    <p><a class="btn ghost" href="%s">Return to store</a></p>
                    """.formatted(id, session.state().name().toLowerCase(), HtmlUtils.htmlEscape(r.successUrl())));
        }
        return shell("""
                <p class="badge">SANDBOX — no real money moves</p>
                <h1>Pay %s</h1>
                <p>Order <strong>%s</strong></p>
                <p class="muted">Card details would be collected here by the payment provider, never by the store.</p>
                <form method="post" action="/api/sandbox-provider/checkout/%s/pay"><button class="btn">Pay now (success)</button></form>
                <form method="post" action="/api/sandbox-provider/checkout/%s/decline"><button class="btn danger">Decline card (failure)</button></form>
                <p><a class="btn ghost" href="%s">Cancel and return to store</a></p>
                """.formatted(amount, order, id, id, HtmlUtils.htmlEscape(r.cancelUrl())));
    }

    private static String shell(String body) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sandbox Payments</title>
                <style>
                  body{font-family:system-ui,sans-serif;background:#f4f5f7;color:#1d2433;margin:0;display:grid;place-items:center;min-height:100vh}
                  main{background:#fff;padding:32px;border-radius:12px;box-shadow:0 4px 24px rgba(0,0,0,.08);max-width:420px;width:calc(100%% - 32px)}
                  h1{font-size:1.5rem;margin:.25rem 0 1rem}.muted{color:#5b6475;font-size:.9rem}
                  .badge{display:inline-block;background:#fff4d6;color:#7a5600;padding:4px 10px;border-radius:999px;font-size:.75rem;font-weight:600}
                  form{margin:10px 0}.btn{display:block;width:100%%;padding:12px;border:0;border-radius:8px;background:#1d2433;color:#fff;font-size:1rem;cursor:pointer;text-align:center;text-decoration:none;box-sizing:border-box}
                  .btn.danger{background:#b42318}.btn.ghost{background:transparent;color:#1d2433;border:1px solid #c9ced8}
                </style></head><body><main>%s</main></body></html>
                """.formatted(body);
    }
}
