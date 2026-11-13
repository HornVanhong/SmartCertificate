package com.smartcertificate.common.health;

import com.smartcertificate.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service and database readiness checks")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSource = dataSourceProvider.getIfAvailable();
    }

    @GetMapping
    @Operation(summary = "Check API and database health")
    public ResponseEntity<ApiResponse<HealthStatusResponse>> checkHealth() {
        String dbStatus = checkDatabase();
        boolean healthy = !"DOWN".equals(dbStatus);
        HealthStatusResponse payload = new HealthStatusResponse(
                healthy ? "UP" : "DEGRADED", "smart-certificate-backend", dbStatus, Instant.now());
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.ok(payload));
    }

    private String checkDatabase() {
        if (dataSource == null) {
            return "NOT_CONFIGURED";
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
