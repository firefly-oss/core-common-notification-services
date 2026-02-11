# Notification Web Module

Exposes REST endpoints to send notifications.

## Endpoints
- POST `/api/v1/email/send` — body: EmailRequestDTO
- POST `/api/v1/sms/send` — body: SMSRequestDTO
- POST `/api/v1/push` — body: PushNotificationRequest

## Configuration (application.yml)
```yaml
server:
  port: 8080

# Twilio (SMS)
twilio:
  config:
    accountSid: ${TWILIO_ACCOUNT_SID}
    authToken: ${TWILIO_AUTH_TOKEN}
    phoneNumber: "+1XXXXXXXXXX"

# SendGrid (Email)
sendgrid:
  apiKey: ${SENDGRID_API_KEY}

# Firebase (Push)
firebase:
  credentialsPath: /path/to/service-account.json
  projectId: your-project-id

# Resend (Email alternative)
resend:
  apiKey: ${RESEND_API_KEY}
  defaultFrom: noreply@example.com
```

To use Resend instead of SendGrid, replace the dependency in `pom.xml` accordingly.

## Run
```bash
mvn -q -DskipTests -f ../pom.xml package
java -jar target/core-common-notification-services-web-1.0.0-SNAPSHOT.jar
```