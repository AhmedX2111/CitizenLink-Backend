package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.response.DashboardSummaryResponse;
import com.ntg.CitizenLink.dto.agent.response.MyOpenCaseResponse;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;


@Service
public interface DashboardService {

    /**
     * Get dashboard summary with KPIs and status chart data.
     */
    DashboardSummaryResponse getSummary();

    /**
     * Get top 5 open cases assigned to the given user.
     */
    List<MyOpenCaseResponse> getMyOpenCases(UUID userId);
}