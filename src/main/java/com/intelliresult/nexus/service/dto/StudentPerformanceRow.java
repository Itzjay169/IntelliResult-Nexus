package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.Student;
import java.math.BigDecimal;

/**
 * Overview row summarizing a student's academic standing and exam history.
 */
public record StudentPerformanceRow(
        Student student,
        int completedExamsCount,
        BigDecimal averagePercentage,
        boolean pass,
        String latestGrade,
        Integer latestRank,
        BigDecimal latestSgpa,
        BigDecimal currentCgpa
) {
}

