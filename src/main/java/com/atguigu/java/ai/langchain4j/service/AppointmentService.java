package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public interface AppointmentService extends IService<Appointment> {
    Appointment getOne(Appointment appointment);

    boolean hasAvailableSlot(String department, LocalDate date, AppointmentPeriod time, String doctorName);

    BookingResult tryBook(Appointment appointment);

    BookingResult reschedule(Appointment oldAppointment, Appointment newAppointment);
}
