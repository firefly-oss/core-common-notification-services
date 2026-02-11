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


package com.firefly.core.notifications.web.controllers.email.v1;

import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/email")
@Tag(name = "Email Notifications", description = "Email sending operations")
@RequiredArgsConstructor
public class EmailController {

private final EmailService service;

    @Operation(
            summary = "Send email",
            description = "Sends an email using the configured provider",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Email request payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = EmailRequestDTO.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Email sent successfully",
                            content = @Content(
                                    schema = @Schema(implementation = EmailResponseDTO.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid email request",
                            content = @Content
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content
                    )
            }
    )
    @PostMapping("/send")
    public Mono<ResponseEntity<EmailResponseDTO>> sendEmail(@Valid @RequestBody EmailRequestDTO request) {
        log.info("Received request to send email to: {}", request.getTo());
        return service.sendEmail(request)
                // Map the successful result to an HTTP 200 response
                .map(ResponseEntity::ok)
                // Log the email response on success
                .doOnNext(response -> log.info("Email response: {}", response.getBody()))
                // Handle errors gracefully
                .onErrorResume(ex -> {
                    log.error("Error occurred while sending email: {}", ex.getMessage(), ex);
                    // Return an HTTP 500 with a standardized error response
                    return Mono.just(
                            ResponseEntity
                                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(EmailResponseDTO.error(ex.getMessage()))
                    );
                });
    }
}