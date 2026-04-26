package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.entity.FollowUpPlan;
import com.atguigu.java.ai.langchain4j.mapper.FollowUpPlanMapper;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.FollowUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class FollowUpServiceImpl implements FollowUpService {

    @Autowired
    private FollowUpPlanMapper followUpPlanMapper;

    @Autowired
    private AppointmentService appointmentService;

    @Override
    public Long createFollowUpPlan(String username, String idCard, String department, LocalDate recommendedDate,
                                   AppointmentPeriod recommendedTime, String doctorName, String reason) {
        FollowUpPlan plan = new FollowUpPlan();
        plan.setUsername(username);
        plan.setIdCard(idCard);
        plan.setDepartment(department);
        plan.setDoctorName(doctorName);
        plan.setRecommendedDate(recommendedDate);
        plan.setRecommendedTime(recommendedTime);
        plan.setReason(reason);
        plan.setStatus("CREATED");
        plan.setCreatedAt(LocalDateTime.now());
        followUpPlanMapper.insert(plan);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult confirmFollowUp(Long planId) {
        FollowUpPlan plan = followUpPlanMapper.selectById(planId);
        if (plan == null) {
            return BookingResult.fail("未找到复诊计划，请先创建复诊计划。");
        }
        if (!"CREATED".equalsIgnoreCase(plan.getStatus())) {
            return BookingResult.fail("该复诊计划状态不可预约，当前状态：" + plan.getStatus());
        }

        Appointment appointment = new Appointment();
        appointment.setUsername(plan.getUsername());
        appointment.setIdCard(plan.getIdCard());
        appointment.setDepartment(plan.getDepartment());
        appointment.setDate(plan.getRecommendedDate());
        appointment.setTime(plan.getRecommendedTime());
        appointment.setDoctorName(plan.getDoctorName());

        BookingResult result = appointmentService.tryBook(appointment);
        if (result.success()) {
            plan.setStatus("BOOKED");
            followUpPlanMapper.updateById(plan);
        }
        return result;
    }
}
