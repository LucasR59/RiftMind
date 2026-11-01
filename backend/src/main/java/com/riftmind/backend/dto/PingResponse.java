package com.riftmind.backend.dto;

import java.time.Instant;

public record PingResponse(String status, String service, Instant timestamp) {
}
