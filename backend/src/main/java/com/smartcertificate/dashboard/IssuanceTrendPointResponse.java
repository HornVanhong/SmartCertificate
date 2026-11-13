package com.smartcertificate.dashboard;

import java.time.LocalDate;

public record IssuanceTrendPointResponse(LocalDate date, long issuedCount) {}
