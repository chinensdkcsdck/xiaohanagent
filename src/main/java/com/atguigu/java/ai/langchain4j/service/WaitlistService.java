package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;

import java.time.LocalDate;

public interface WaitlistService {

    Long createAppointmentWaitlist(String username, String idCard, String department, LocalDate date,
                                   AppointmentPeriod time, String doctorName, String note);

    Long createExamWaitlist(String username, String idCard, String examType, LocalDate date,
                            AppointmentPeriod time, String doctorName, String note);
}
