# Notification Providers Configuration

This microservice supports multiple notification providers for email, SMS, and push notifications. Providers are **conditionally instantiated** based on configuration — only providers with required configuration properties will be loaded.

## Available Providers

### Email Providers

#### SendGrid
**Purpose:** Transactional email delivery via SendGrid API

**Configuration:**
```yaml
sendgrid:
  api-key: ${SENDGRID_API_KEY}
```

**Required Environment Variables:**
- `SENDGRID_API_KEY` - Your SendGrid API key

**To Disable:** Comment out or remove the `sendgrid` configuration block

---

#### Resend
**Purpose:** Modern transactional email delivery via Resend API

**Configuration:**
```yaml
resend:
  api-key: ${RESEND_API_KEY}
  default-from: "noreply@example.com"
```

**Required Environment Variables:**
- `RESEND_API_KEY` - Your Resend API key

**Optional:**
- `default-from` - Default sender email address (used when request doesn't specify one)

**To Disable:** Comment out or remove the `resend` configuration block

**Note:** By default, Resend is commented out in `application.yaml`

---

### SMS Provider

#### Twilio
**Purpose:** SMS delivery via Twilio API

**Configuration:**
```yaml
twilio:
  config:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    phone-number: ${TWILIO_PHONE_NUMBER}
```

**Required Environment Variables:**
- `TWILIO_ACCOUNT_SID` - Your Twilio account SID
- `TWILIO_AUTH_TOKEN` - Your Twilio auth token
- `TWILIO_PHONE_NUMBER` - Your Twilio phone number (e.g., `+1234567890`)

**To Disable:** Comment out or remove the `twilio` configuration block

---

### Push Notification Provider

#### Firebase Cloud Messaging (FCM)
**Purpose:** Push notifications to mobile devices via Firebase

**Configuration:**
```yaml
firebase:
  project-id: ${FIREBASE_PROJECT_ID}
  credentials-path: ${FIREBASE_CREDENTIALS_PATH}
```

**Required Environment Variables:**
- `FIREBASE_PROJECT_ID` - Your Firebase project ID
- `FIREBASE_CREDENTIALS_PATH` - Path to Firebase service account JSON file (optional, uses Google Application Default Credentials if not set)

**To Disable:** Comment out or remove the `firebase` configuration block

---

## How It Works

### Conditional Loading

Each provider uses Spring Boot's `@ConditionalOnProperty` annotation to ensure it's only instantiated when the required configuration is present:

- **Twilio**: Loaded only if `twilio.config.account-sid` is set
- **SendGrid**: Loaded only if `sendgrid.api-key` is set
- **Resend**: Loaded only if `resend.api-key` is set
- **Firebase**: Loaded only if `firebase.project-id` is set

### Startup Logs

When a provider is successfully initialized, you'll see log messages like:

```
INFO  Initializing Twilio SMS provider with account SID: ACxxxx...
INFO  Initializing SendGrid email provider
INFO  Initializing Resend email provider with base URL: https://api.resend.com
INFO  Initializing Firebase Cloud Messaging provider for project: my-project
```

If a provider's configuration is missing, it will **not** be instantiated and you won't see its initialization log.

---

## Configuration Profiles

The microservice supports multiple Spring profiles (`dev`, `testing`, `prod`). You can use environment-specific configuration files:

- `application.yaml` - Base configuration
- `application-dev.yaml` - Development overrides
- `application-prod.yaml` - Production overrides

---

## Email Provider Selection

Select which email provider to instantiate via configuration (others will be ignored at startup):

```yaml
notifications:
  email:
    provider: resend   # or sendgrid
```

Then configure only that provider's credentials (e.g., `resend.api-key` or `sendgrid.api-key`). Do not configure more than one email provider at once.

If multiple providers are configured, only the one matching `notifications.email.provider` will be loaded; the rest will be ignored. If none is selected, no email adapter is created.

---

## Security Best Practices

1. **Never commit credentials** to version control
2. Use **environment variables** for all sensitive data (API keys, tokens, etc.)
3. In production, use **secrets management** (e.g., AWS Secrets Manager, HashiCorp Vault)
4. Rotate API keys regularly
5. Use different credentials for each environment (dev, staging, prod)

---

## Example: Enabling Only Resend

If you only want to use Resend for emails:

1. Comment out SendGrid configuration in `application.yaml`
2. Uncomment the Resend configuration:

```yaml
# SendGrid Email Provider - DISABLED
#sendgrid:
# api-key: ${SENDGRID_API_KEY}

# Resend Email Provider - ENABLED
resend:
  api-key: ${RESEND_API_KEY}
  default-from: "notifications@yourdomain.com"
```

3. Set the environment variable:
```bash
export RESEND_API_KEY=re_your_actual_api_key
```

4. Start the application — only Resend will be initialized

---

## Troubleshooting

### Provider Not Loading

**Symptom:** Expected provider initialization log message doesn't appear

**Solutions:**
1. Verify the required configuration property is set in `application.yaml`
2. Check that the environment variable is set: `echo $VARIABLE_NAME`
3. Ensure there are no typos in property names (they're case-sensitive)
4. Check application logs for Spring Boot property binding errors

### Multiple Providers Conflict

**Symptom:** Both email providers are loaded but you only want one

**Solution:** Comment out the unwanted provider's configuration in `application.yaml`

### Missing Dependency

**Symptom:** `ClassNotFoundException` or `NoClassDefFoundError`

**Solution:** Verify the provider's library is included in `pom.xml`:
```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-notifications-resend</artifactId>
    <version>${project.version}</version>
</dependency>
```
