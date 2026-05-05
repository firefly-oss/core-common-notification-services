/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.core.notifications.web.config.v1;

import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.core.services.email.v1.EmailServiceImpl;
import org.fireflyframework.notifications.core.services.push.v1.PushService;
import org.fireflyframework.notifications.core.services.push.v1.PushServiceImpl;
import org.fireflyframework.notifications.core.services.sms.v1.SMSService;
import org.fireflyframework.notifications.core.services.sms.v1.SMSServiceImpl;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.fireflyframework.notifications.interfaces.providers.push.v1.PushProvider;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes channel services only when the corresponding provider is configured.
 * Without this gate, the framework's @Service-tagged service impls are scanned
 * unconditionally and fail at startup if their provider bean is absent.
 */
@Configuration
public class NotificationServicesConfig {

    @Bean
    @ConditionalOnBean(EmailProvider.class)
    @ConditionalOnMissingBean(EmailService.class)
    public EmailService emailService() {
        return new EmailServiceImpl();
    }

    @Bean
    @ConditionalOnBean(SMSProvider.class)
    @ConditionalOnMissingBean(SMSService.class)
    public SMSService smsService(SMSProvider smsProvider) {
        return new SMSServiceImpl(smsProvider);
    }

    @Bean
    @ConditionalOnBean(PushProvider.class)
    @ConditionalOnMissingBean(PushService.class)
    public PushService pushService() {
        return new PushServiceImpl();
    }
}
