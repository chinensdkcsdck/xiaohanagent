package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;

import java.time.LocalDate;

public interface ExamService {

    boolean hasAvailableExamSlot(String examType, LocalDate date, AppointmentPeriod time, String doctorName);

    BookingResult tryBookExam(ExamBookingRequest request);

    BookingResult cancelExamBooking(ExamBookingRequest request);
}
