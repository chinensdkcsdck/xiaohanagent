package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.entity.WaitlistRequest;
import com.atguigu.java.ai.langchain4j.mapper.WaitlistRequestMapper;
import com.atguigu.java.ai.langchain4j.service.WaitlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class WaitlistServiceImpl implements WaitlistService {

    @Autowired
    private WaitlistRequestMapper waitlistRequestMapper;

    @Override
    public Long createAppointmentWaitlist(String username, String idCard, String department, LocalDate date,
                                          AppointmentPeriod time, String doctorName, String note) {
        WaitlistRequest request = new WaitlistRequest();
        request.setBizType("APPOINTMENT");
        request.setUsername(username);
        request.setIdCard(idCard);
        request.setDepartment(department);
        request.setDate(date);
        request.setTime(time);
        request.setDoctorName(doctorName);
        request.setStatus("PENDING");
        request.setNote(note);
        request.setCreatedAt(LocalDateTime.now());
        waitlistRequestMapper.insert(request);
        return request.getId();
    }

    @Override
    public Long createExamWaitlist(String username, String idCard, String examType, LocalDate date,
                                   AppointmentPeriod time, String doctorName, String note) {
        WaitlistRequest request = new WaitlistRequest();
        request.setBizType("EXAM");
        request.setUsername(username);
        request.setIdCard(idCard);
        request.setExamType(examType);
        request.setDate(date);
        request.setTime(time);
        request.setDoctorName(doctorName);
        request.setStatus("PENDING");
        request.setNote(note);
        request.setCreatedAt(LocalDateTime.now());
        waitlistRequestMapper.insert(request);
        return request.getId();
    }
}
