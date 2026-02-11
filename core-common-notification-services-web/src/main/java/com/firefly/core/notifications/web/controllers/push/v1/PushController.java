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


package com.firefly.core.notifications.web.controllers.push.v1;

import org.fireflyframework.notifications.core.services.push.v1.PushService;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationRequest;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/push")
@Tag(name = "Push Notifications", description = "APIs for sending push notifications")
public class PushController {

    @Autowired
private PushService service;

    @PostMapping
    @Operation(
            summary = "Send a push notification",
            description = "This endpoint is used to send push notifications to a given device token.",
            requestBody = @RequestBody(
                    description = "Details of the push notification to be sent",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = PushNotificationRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Push notification sent successfully",
                            content = @Content(
                                    schema = @Schema(implementation = PushNotificationResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request",
                            content = @Content
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content
                    )
            }
    )
    public Mono<ResponseEntity<PushNotificationResponse>> sendPush(
            @org.springframework.web.bind.annotation.RequestBody PushNotificationRequest request
    ) {
        return service.sendPush(request)
                .map(ResponseEntity::ok);
    }
}
