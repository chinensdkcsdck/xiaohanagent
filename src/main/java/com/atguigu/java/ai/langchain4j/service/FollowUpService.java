package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;

import java.time.LocalDate;

public interface FollowUpService {

    Long createFollowUpPlan(String username, String idCard, String department, LocalDate recommendedDate,
                            AppointmentPeriod recommendedTime, String doctorName, String reason);

    BookingResult confirmFollowUp(Long planId);
}
