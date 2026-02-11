/*
 * Copyright 2025 Firefly Software Solutions Inc
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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ProviderSelectionConfig {

    @Autowired(required = false)
    private NotificationsSelectionProperties props;

    @PostConstruct
    public void logProviderSelection() {
        if (props != null && props.getEmail() != null) {
            String selected = props.getEmail().getProvider();
            if (selected != null && !selected.isBlank()) {
                log.info("notifications.email.provider='{}' — only that adapter will be instantiated.", selected);
            } else {
                log.info("notifications.email.provider not set — no email adapter will be loaded.");
            }
        }
    }
}
