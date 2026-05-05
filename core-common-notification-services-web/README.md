# core-common-notification-services-web

Spring Boot WebFlux application that exposes the unified Email / SMS / Push notification API over HTTP. The web module is the container-runnable artifact of the `core-common-notification-services` repo — it owns the controllers, the application bootstrap, the provider wiring, and the deployment-time configuration contract.

The actual sending logic (provider clients, request marshalling, retries) lives in the upstream framework artifacts under `org.fireflyframework:fireflyframework-notifications-*`. This module composes those artifacts into a runnable service and decides **which channels light up at deploy time** based on the env-var configuration the operator provides.

---

## 1. What this module is and how it boots

`NotificationApplication` is a standard `@SpringBootApplication` with a deliberate component-scan footprint:

```java
@SpringBootApplication(scanBasePackages = {
    "com.firefly.core.notifications",   // this module's controllers + config
    "org.fireflyframework.web"          // shared web platform (errors, filters, security)
})
```

It does **not** scan `org.fireflyframework.notifications` directly. The framework's `EmailServiceImpl` / `SMSServiceImpl` / `PushServiceImpl` classes are `@Service`-annotated, but pulling them in via component scan would force every channel's provider to be present at startup or the app would fail to construct the impl. Instead, the wiring goes through a small `@Configuration` in this module:

```
src/main/java/.../web/config/v1/NotificationServicesConfig.java
```

It exposes one bean per channel, each gated on its provider:

| Bean | Created when |
|---|---|
| `EmailService` (= `new EmailServiceImpl()`) | An `EmailProvider` bean exists in the context |
| `SMSService` (= `new SMSServiceImpl(SMSProvider)`) | An `SMSProvider` bean exists |
| `PushService` (= `new PushServiceImpl()`) | A `PushProvider` bean exists |

The provider beans themselves come from the framework's adapter jars (sendgrid, resend, twilio, firebase) and are created by their `@AutoConfiguration` classes only when the matching properties are set (see §3).

The three controllers (`EmailController`, `SMSController`, `PushController`) inject their service via `@Autowired(required = false)` and short-circuit to **HTTP 503 Service Unavailable** when the bean is absent. This means:

- **The controllers are always registered** — the OpenAPI spec at `/v3/api-docs` always advertises all three endpoints, so the SDK module ships a complete API surface regardless of which providers were configured at spec-generation time.
- **Channels left unconfigured do not block startup** — the app boots cleanly with zero providers; the channel endpoints return a documented 503 with a "provider not configured" message until the operator sets the relevant env vars.
- **Channels with a provider work normally** — sendgrid only, sendgrid + twilio, all three, etc. are all valid deploy modes.

---

## 2. HTTP surface

All three endpoints accept `application/json` and return `application/json`. Full request/response schemas are emitted at runtime to `/v3/api-docs` (OpenAPI 3) and rendered at `/swagger-ui.html` (in `dev` and `testing` profiles).

### `POST /api/v1/email/send`

Body: `EmailRequestDTO` from `org.fireflyframework.notifications.interfaces.dtos.email.v1`.

```json
{
  "to": "alice@example.com",
  "from": "noreply@yourdomain.com",
  "subject": "Welcome",
  "body": "<h1>Hello</h1>",
  "attachments": [
    { "filename": "terms.pdf", "content": "<base64>", "contentType": "application/pdf" }
  ]
}
```

Responses:
- `200 OK` → `EmailResponseDTO` with provider message id and `status="SENT"`.
- `400 Bad Request` → validation failure on the request body.
- `500 Internal Server Error` → provider call threw; the body contains `EmailResponseDTO.error(<provider error message>)`.
- `503 Service Unavailable` → no email provider is configured on this deployment.

### `POST /api/v1/sms/send`

Body: `SMSRequestDTO`.

```json
{ "phoneNumber": "+14155551234", "message": "Your code: 123456" }
```

Responses: same shape as above (`SMSResponseDTO` with `messageId` / `status` / `errorMessage`). 503 when no SMS provider is configured.

### `POST /api/v1/push`

Body: `PushNotificationRequest`.

```json
{
  "token": "<device FCM token>",
  "title": "New message",
  "body": "You have a new message from Alice.",
  "data": { "threadId": "42" }
}
```

Responses: `PushNotificationResponse(messageId, success, errorMessage)`. 503 when no push provider is configured.

---

## 3. Configuration model

All runtime configuration is bound under the **`firefly.notifications.*`** property prefix. This is the prefix the framework's own `@ConfigurationProperties` and `@ConditionalOnProperty` annotations declare in `fireflyframework-notifications-{sendgrid,resend,twilio,firebase}` — you can verify this with `javap -v` on the corresponding `*AutoConfiguration` and `*Properties` classes. Using any other prefix means the framework won't bind the values and no provider bean will be created.

The default `application.yaml` (in `src/main/resources/`) wires every property to an env-var placeholder with empty defaults:

```yaml
firefly:
  notifications:
    email:
      provider: ${NOTIFICATIONS_EMAIL_PROVIDER:}   # sendgrid | resend
    sms:
      provider: ${NOTIFICATIONS_SMS_PROVIDER:}     # twilio
    push:
      provider: ${NOTIFICATIONS_PUSH_PROVIDER:}    # firebase

    sendgrid:
      api-key: ${SENDGRID_API_KEY:}

    resend:
      api-key:      ${RESEND_API_KEY:}
      default-from: ${RESEND_DEFAULT_FROM:}
      base-url:     ${RESEND_BASE_URL:}

    twilio:
      account-sid:  ${TWILIO_ACCOUNT_SID:}
      auth-token:   ${TWILIO_AUTH_TOKEN:}
      phone-number: ${TWILIO_PHONE_NUMBER:}

    firebase:
      project-id:       ${FIREBASE_PROJECT_ID:}
      credentials-path: ${FIREBASE_CREDENTIALS_PATH:}
```

Any of three forms works:

1. **Env vars** with Spring Boot relaxed binding. `FIREFLY_NOTIFICATIONS_SENDGRID_API_KEY=xxx` ↔ `firefly.notifications.sendgrid.api-key=xxx`.
2. **JVM system properties.** `-Dfirefly.notifications.email.provider=sendgrid`.
3. **External `application.yaml`** mounted into the container or `--spring.config.location=…`.

### Channel selection

A channel is "enabled" if **and only if** its `provider` selector is set to one of the supported values **and** the credentials for that selector are also set. Examples:

- Email-only deploy with SendGrid: set `FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER=sendgrid` + `FIREFLY_NOTIFICATIONS_SENDGRID_API_KEY`. SMS and Push endpoints stay at 503.
- Email + SMS: also set `FIREFLY_NOTIFICATIONS_SMS_PROVIDER=twilio` and the three `FIREFLY_NOTIFICATIONS_TWILIO_*` vars.
- All three: add `FIREFLY_NOTIFICATIONS_PUSH_PROVIDER=firebase` and the two `FIREFLY_NOTIFICATIONS_FIREBASE_*` vars.
- Email via Resend instead of SendGrid: `FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER=resend` + the three `FIREFLY_NOTIFICATIONS_RESEND_*` vars. The SendGrid jar can stay on the classpath; its autoconfig is gated on `firefly.notifications.email.provider=sendgrid` and stays inactive.

### Provider env-var reference (for operators)

| Channel | Selector | Provider | Required vars |
|---|---|---|---|
| Email | `FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER=sendgrid` | SendGrid | `FIREFLY_NOTIFICATIONS_SENDGRID_API_KEY` |
| Email | `FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER=resend` | Resend | `FIREFLY_NOTIFICATIONS_RESEND_API_KEY`, `FIREFLY_NOTIFICATIONS_RESEND_DEFAULT_FROM`. Optional: `FIREFLY_NOTIFICATIONS_RESEND_BASE_URL` |
| SMS | `FIREFLY_NOTIFICATIONS_SMS_PROVIDER=twilio` | Twilio | `FIREFLY_NOTIFICATIONS_TWILIO_ACCOUNT_SID`, `FIREFLY_NOTIFICATIONS_TWILIO_AUTH_TOKEN`, `FIREFLY_NOTIFICATIONS_TWILIO_PHONE_NUMBER` (E.164) |
| Push | `FIREFLY_NOTIFICATIONS_PUSH_PROVIDER=firebase` | Firebase Cloud Messaging | `FIREFLY_NOTIFICATIONS_FIREBASE_PROJECT_ID`, `FIREFLY_NOTIFICATIONS_FIREBASE_CREDENTIALS_PATH` (filesystem path inside the container, not the JSON content itself — mount the file via Kubernetes Secret / CSI / Docker bind) |

### Other operational vars (already in `application.yaml`)

| Var | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Listening port |
| `SERVER_ADDRESS` | `localhost` | Bind address — set to `0.0.0.0` in containers/k8s |
| `SPRING_PROFILES_ACTIVE` | unset | Optional. `dev` / `testing` enable Swagger UI; `prod` quiets logging |

---

## 4. Build

The web module is a Maven submodule of `core-common-notification-services`; build from the repo root:

```bash
mvn clean install -DskipTests
```

Build artifacts:
- `core-common-notification-services-web/target/core-common-notification-services.jar` — repackaged Spring Boot fat jar (the `<finalName>` is `${project.parent.artifactId}`, hence the parent name).
- `core-common-notification-services-sdk/target/openapi.yml` is regenerated by the `generate-openapi` profile inherited from `firefly-parent`. The profile boots `OpenApiGenApplication` (a stripped-down context) and dumps the live `/v3/api-docs` to `core-common-notification-services-sdk/src/main/resources/openapi.yml`. The SDK module then runs `openapi-generator-maven-plugin` against that spec to produce the typed Java client published as `com.firefly:core-common-notification-services-sdk`.

To skip OpenAPI regeneration during a quick iteration:

```bash
mvn clean install -DskipTests -DskipOpenApiGen
```

The `-DskipOpenApiGen` flag is what CI uses (the build runners have no Postgres/network for the boot step); the committed `openapi.yml` in the SDK module is regenerated on developer machines and pushed.

---

## 5. Run locally

### Without any providers (smoke test)

```bash
java -jar target/core-common-notification-services.jar
```

The app starts in ~12s. Hitting any channel endpoint returns 503 with a "provider not configured" message. `/actuator/health` returns 200.

### With SendGrid configured

```bash
java \
  -Dfirefly.notifications.email.provider=sendgrid \
  -Dfirefly.notifications.sendgrid.api-key=$SENDGRID_API_KEY \
  -jar target/core-common-notification-services.jar
```

`POST /api/v1/email/send` now reaches the SendGrid client. SMS and Push remain 503.

### With all three providers

```bash
java \
  -Dfirefly.notifications.email.provider=sendgrid \
  -Dfirefly.notifications.sendgrid.api-key=$SENDGRID_API_KEY \
  -Dfirefly.notifications.sms.provider=twilio \
  -Dfirefly.notifications.twilio.account-sid=$TWILIO_ACCOUNT_SID \
  -Dfirefly.notifications.twilio.auth-token=$TWILIO_AUTH_TOKEN \
  -Dfirefly.notifications.twilio.phone-number=+14155551234 \
  -Dfirefly.notifications.push.provider=firebase \
  -Dfirefly.notifications.firebase.project-id=$FIREBASE_PROJECT_ID \
  -Dfirefly.notifications.firebase.credentials-path=/path/to/firebase-service-account.json \
  -jar target/core-common-notification-services.jar
```

---

## 6. Deploy as a container

The image is built by the org-shared `build-image.yml` workflow (Paketo Buildpacks, ARM64) and pushed to GHCR:

```
ghcr.io/firefly-oss/core-common-notification-services-web:latest
ghcr.io/firefly-oss/core-common-notification-services-web:1.0.0-SNAPSHOT
```

Minimal Kubernetes Deployment skeleton (channels enabled at the env layer):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-services
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: web
        image: ghcr.io/firefly-oss/core-common-notification-services-web:1.0.0-SNAPSHOT
        ports:
        - containerPort: 8080
        env:
        - name: SERVER_ADDRESS
          value: "0.0.0.0"
        # --- Email (SendGrid) ---
        - name: FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER
          value: "sendgrid"
        - name: FIREFLY_NOTIFICATIONS_SENDGRID_API_KEY
          valueFrom: { secretKeyRef: { name: notif-credentials, key: sendgrid-api-key } }
        # --- SMS (Twilio) ---
        - name: FIREFLY_NOTIFICATIONS_SMS_PROVIDER
          value: "twilio"
        - name: FIREFLY_NOTIFICATIONS_TWILIO_ACCOUNT_SID
          valueFrom: { secretKeyRef: { name: notif-credentials, key: twilio-account-sid } }
        - name: FIREFLY_NOTIFICATIONS_TWILIO_AUTH_TOKEN
          valueFrom: { secretKeyRef: { name: notif-credentials, key: twilio-auth-token } }
        - name: FIREFLY_NOTIFICATIONS_TWILIO_PHONE_NUMBER
          value: "+14155551234"
        # --- Push (Firebase) — credentials JSON mounted as a file ---
        - name: FIREFLY_NOTIFICATIONS_PUSH_PROVIDER
          value: "firebase"
        - name: FIREFLY_NOTIFICATIONS_FIREBASE_PROJECT_ID
          valueFrom: { secretKeyRef: { name: notif-credentials, key: firebase-project-id } }
        - name: FIREFLY_NOTIFICATIONS_FIREBASE_CREDENTIALS_PATH
          value: "/var/secrets/firebase/service-account.json"
        volumeMounts:
        - name: firebase-credentials
          mountPath: /var/secrets/firebase
          readOnly: true
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 8080 }
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 8080 }
      volumes:
      - name: firebase-credentials
        secret:
          secretName: notif-credentials
          items:
          - key: firebase-service-account.json
            path: service-account.json
```

If a channel is not needed in this deployment, omit its `*_PROVIDER` selector and credentials — the corresponding endpoint will return 503 with a clear message and the rest of the app keeps working.

---

## 7. Operational endpoints

| Path | Purpose |
|---|---|
| `/actuator/health` | Aggregate health (`UP` / `DOWN`) |
| `/actuator/health/liveness` | Liveness probe target |
| `/actuator/health/readiness` | Readiness probe target |
| `/actuator/info` | Build info (populated by `build-info` Spring Boot goal) |
| `/actuator/prometheus` | Prometheus scrape endpoint (Micrometer) |
| `/v3/api-docs` | OpenAPI 3 spec (always full surface, includes Email/SMS/Push paths) |
| `/swagger-ui.html` | Swagger UI (enabled in `dev` and `testing` profiles only) |

Note: liveness/readiness do **not** flip to DOWN when a channel provider is missing — that's a configuration choice, not a fault. Channel availability is observable via the 503 responses on the channel endpoints themselves.

---

## 8. Verifying a deploy

After redeploying with new env vars, the operator should run these checks:

1. `GET /actuator/health` → `200 {"status":"UP"}` confirms the JVM, Netty, and the auto-configured beans started cleanly.
2. `GET /v3/api-docs | jq '.paths | keys'` → must include `/api/v1/email/send`, `/api/v1/sms/send`, `/api/v1/push`.
3. For each enabled channel, send a real request:
   - **200** → channel is wired and the underlying provider was called.
   - **503** with `"<channel> provider not configured…"` → the env vars for that channel didn't reach the JVM. Likely causes: typo in the var name, wrong prefix (must be `FIREFLY_NOTIFICATIONS_*`), or env not being merged into the runtime (e.g., set on the build agent instead of the pod spec).
   - **5xx** with a provider-specific message → the channel is wired but the provider rejected the request (auth, rate limit, malformed payload). Inspect the `errorMessage` field.

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| App fails to start with `Field <x>Provider in <…>ServiceImpl required a bean of type '<…>Provider'` | Old image without the conditional wiring — provider missing forced impl construction to fail | Redeploy a build that includes `NotificationServicesConfig` and the `@Autowired(required=false)` controllers (HEAD of `main` after the wire-up fix) |
| App starts but every channel returns 503 | No provider selectors set | Set at least one `FIREFLY_NOTIFICATIONS_<channel>_PROVIDER` and its credentials |
| Email returns 503 even with SendGrid creds set | `FIREFLY_NOTIFICATIONS_EMAIL_PROVIDER` is unset or not equal to `sendgrid` | The credential vars alone don't activate the channel; the selector is what triggers the auto-config |
| OpenAPI spec at `/v3/api-docs` is missing paths | Old build where the controllers were `@ConditionalOnBean` (pre-fix) and the spec was regenerated against an empty context | Rebuild from current `main`; the controllers are now unconditional, so the spec is always full |
| Firebase boot fails with `Your default credentials were not found` | `FIREFLY_NOTIFICATIONS_FIREBASE_CREDENTIALS_PATH` points at a path that doesn't exist inside the container | Verify the secret is mounted at the path you set; remember the value is a *path*, not the JSON contents |
| Twilio rejects with `21211 Invalid 'To' phone number` | Recipient number isn't in E.164 | Always pass numbers as `+<country><subscriber>`; the configured `phone-number` (Twilio sender) must also be in E.164 |

---

## 10. Source layout

```
core-common-notification-services-web/
├── pom.xml
└── src/main/
    ├── java/com/firefly/core/notifications/web/
    │   ├── NotificationApplication.java                # @SpringBootApplication + scan footprint
    │   ├── config/v1/
    │   │   ├── NotificationServicesConfig.java         # Conditional EmailService/SMSService/PushService beans
    │   │   ├── NotificationsSelectionProperties.java   # @ConfigurationProperties("firefly.notifications")
    │   │   └── ProviderSelectionConfig.java            # Logs which email provider was selected at startup
    │   ├── controllers/email/v1/EmailController.java
    │   ├── controllers/sms/v1/SMSController.java
    │   ├── controllers/push/v1/PushController.java
    │   └── openapi/OpenApiGenApplication.java          # Headless boot used by the generate-openapi profile
    └── resources/
        ├── application.yaml                            # Runtime config (firefly.notifications.* keys + actuator/logging/profiles)
        └── application-example.yml                     # Reference snippet for operators
```

The framework artifacts that supply the actual sending logic live outside this module:

- `org.fireflyframework:fireflyframework-notifications-core` — `EmailService`/`SMSService`/`PushService` interfaces and impls, request/response DTOs, the `NotificationTemplateEngine` interface (Freemarker default).
- `org.fireflyframework:fireflyframework-notifications-sendgrid` — `SendGridAutoConfiguration` + `SendGridProperties`, gated on `firefly.notifications.email.provider=sendgrid` and `firefly.notifications.sendgrid.api-key`.
- `org.fireflyframework:fireflyframework-notifications-resend` — same shape, gated on `firefly.notifications.email.provider=resend`.
- `org.fireflyframework:fireflyframework-notifications-twilio` — gated on `firefly.notifications.sms.provider=twilio`.
- `org.fireflyframework:fireflyframework-notifications-firebase` — gated on `firefly.notifications.push.provider=firebase`.

Adding a new provider in the future means publishing a new framework adapter that contributes a `<channel>Provider` bean and gates it on a new selector value (e.g. `firefly.notifications.email.provider=mailgun`). No changes are required in this web module — the conditional `@Bean` methods in `NotificationServicesConfig` will pick up any provider implementing the existing port interfaces.
