package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.config.LockProperties;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.entity.ExamBooking;
import com.atguigu.java.ai.langchain4j.entity.ExamSlot;
import com.atguigu.java.ai.langchain4j.lock.DistributedLockClient;
import com.atguigu.java.ai.langchain4j.mapper.ExamBookingMapper;
import com.atguigu.java.ai.langchain4j.mapper.ExamSlotMapper;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.ExamBookingRequest;
import com.atguigu.java.ai.langchain4j.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ExamServiceImpl implements ExamService {

    @Value("${app.exam.default-capacity:20}")
    private int defaultExamCapacity;

    @Autowired
    private ExamSlotMapper examSlotMapper;

    @Autowired
    private ExamBookingMapper examBookingMapper;

    @Autowired
    private DistributedLockClient distributedLockClient;

    @Autowired
    private LockProperties lockProperties;

    @Autowired
    private MeterRegistry meterRegistry;

    @Override
    public boolean hasAvailableExamSlot(String examType, LocalDate date, AppointmentPeriod time, String doctorName) {
        ExamSlot slot = getOrCreateSlot(examType, date, time, doctorName);
        int reserved = slot.getReserved() == null ? 0 : slot.getReserved();
        return reserved < slot.getCapacity();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult tryBookExam(ExamBookingRequest request) {
        String lockKey = buildLockKey(request.getExamType(), request.getDate(), request.getTime(), request.getDoctorName());
        if (!distributedLockClient.tryLock(lockKey, lockProperties.getWaitMs(), lockProperties.getLeaseMs())) {
            count("app.exam.lock.fail");
            return BookingResult.fail("当前检查预约人数较多，请稍后重试");
        }

        try {
            LambdaQueryWrapper<ExamBooking> duplication = new LambdaQueryWrapper<>();
            duplication.eq(ExamBooking::getUsername, request.getUsername())
                    .eq(ExamBooking::getIdCard, request.getIdCard())
                    .eq(ExamBooking::getExamType, request.getExamType())
                    .eq(ExamBooking::getDate, request.getDate())
                    .eq(ExamBooking::getTime, request.getTime())
                    .eq(ExamBooking::getStatus, "BOOKED")
                    .last("LIMIT 1");
            ExamBooking existing = examBookingMapper.selectOne(duplication);
            if (existing != null) {
                count("app.exam.duplicate");
                return BookingResult.fail("您已存在同一检查项目和时段的预约，请勿重复预约");
            }

            ExamSlot slot = getOrCreateSlot(request.getExamType(), request.getDate(), request.getTime(), request.getDoctorName());
            int reserved = slot.getReserved() == null ? 0 : slot.getReserved();
            if (reserved >= slot.getCapacity()) {
                count("app.exam.capacity.full");
                return BookingResult.fail("当前检查时段已满，可加入候补队列");
            }

            slot.setReserved(reserved + 1);
            examSlotMapper.updateById(slot);

            ExamBooking booking = new ExamBooking();
            booking.setUsername(request.getUsername());
            booking.setIdCard(request.getIdCard());
            booking.setExamType(request.getExamType());
            booking.setDate(request.getDate());
            booking.setTime(request.getTime());
            booking.setDoctorName(request.getDoctorName());
            booking.setStatus("BOOKED");
            booking.setCreatedAt(LocalDateTime.now());
            examBookingMapper.insert(booking);

            count("app.exam.booking.success");
            return BookingResult.ok("检查预约成功，请按预约时间提前到院");
        } finally {
            distributedLockClient.unlock(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult cancelExamBooking(ExamBookingRequest request) {
        LambdaQueryWrapper<ExamBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamBooking::getUsername, request.getUsername())
                .eq(ExamBooking::getIdCard, request.getIdCard())
                .eq(ExamBooking::getExamType, request.getExamType())
                .eq(ExamBooking::getDate, request.getDate())
                .eq(ExamBooking::getTime, request.getTime())
                .eq(ExamBooking::getStatus, "BOOKED")
                .last("LIMIT 1");
        ExamBooking booking = examBookingMapper.selectOne(wrapper);
        if (booking == null) {
            count("app.exam.cancel.not_found");
            return BookingResult.fail("未找到可取消的检查预约记录");
        }

        booking.setStatus("CANCELLED");
        examBookingMapper.updateById(booking);

        ExamSlot slot = getOrCreateSlot(request.getExamType(), request.getDate(), request.getTime(), request.getDoctorName());
        int reserved = slot.getReserved() == null ? 0 : slot.getReserved();
        slot.setReserved(Math.max(0, reserved - 1));
        examSlotMapper.updateById(slot);
        count("app.exam.cancel.success");
        return BookingResult.ok("检查预约已取消");
    }

    private ExamSlot getOrCreateSlot(String examType, LocalDate date, AppointmentPeriod time, String doctorName) {
        LambdaQueryWrapper<ExamSlot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamSlot::getExamType, examType)
                .eq(ExamSlot::getDate, date)
                .eq(ExamSlot::getTime, time);
        if (StringUtils.hasText(doctorName)) {
            wrapper.eq(ExamSlot::getDoctorName, doctorName.trim());
        } else {
            wrapper.isNull(ExamSlot::getDoctorName);
        }
        wrapper.last("LIMIT 1");
        ExamSlot slot = examSlotMapper.selectOne(wrapper);
        if (slot != null) {
            return slot;
        }

        ExamSlot created = new ExamSlot();
        created.setExamType(examType);
        created.setDate(date);
        created.setTime(time);
        created.setDoctorName(StringUtils.hasText(doctorName) ? doctorName.trim() : null);
        created.setCapacity(defaultExamCapacity);
        created.setReserved(0);
        examSlotMapper.insert(created);
        return created;
    }

    private String buildLockKey(String examType, LocalDate date, AppointmentPeriod time, String doctorName) {
        String doctor = StringUtils.hasText(doctorName) ? doctorName.trim() : "*";
        return "exam:" + examType + ":" + date + ":" + time + ":" + doctor;
    }

    private void count(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }
}
