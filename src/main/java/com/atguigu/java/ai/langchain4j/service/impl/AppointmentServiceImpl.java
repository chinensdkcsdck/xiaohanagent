package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.config.LockProperties;
import com.atguigu.java.ai.langchain4j.lock.DistributedLockClient;
import com.atguigu.java.ai.langchain4j.mapper.AppointmentMapper;
import com.atguigu.java.ai.langchain4j.mq.BookingEventPublisher;
import com.atguigu.java.ai.langchain4j.mq.event.BookingCreatedEvent;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentServiceImpl extends ServiceImpl<AppointmentMapper, Appointment>
        implements AppointmentService {

    @Value("${app.appointment.default-capacity:20}")
    private int defaultCapacity;

    @Value("${app.appointment.department-capacity.neike:30}")
    private int neikeCapacity;

    @Value("${app.appointment.department-capacity.erke:20}")
    private int erkeCapacity;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DistributedLockClient distributedLockClient;

    @Autowired
    private LockProperties lockProperties;

    @Autowired(required = false)
    private BookingEventPublisher bookingEventPublisher;

    @Override
    public Appointment getOne(Appointment appointment) {
        LambdaQueryWrapper<Appointment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Appointment::getUsername, appointment.getUsername());
        queryWrapper.eq(Appointment::getIdCard, appointment.getIdCard());
        queryWrapper.eq(Appointment::getDepartment, appointment.getDepartment());
        queryWrapper.eq(Appointment::getDate, appointment.getDate());
        queryWrapper.eq(Appointment::getTime, appointment.getTime());
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public boolean hasAvailableSlot(String department, LocalDate date, AppointmentPeriod time, String doctorName) {
        LambdaQueryWrapper<Appointment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Appointment::getDepartment, department)
                .eq(Appointment::getDate, date)
                .eq(Appointment::getTime, time);
        if (StringUtils.hasText(doctorName)) {
            queryWrapper.eq(Appointment::getDoctorName, doctorName);
        }
        long bookedCount = baseMapper.selectCount(queryWrapper);
        return bookedCount < resolveCapacity(department);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult tryBook(Appointment appointment) {
        String lockKey = buildSlotLockKey(appointment);
        if (!tryAcquireLock(lockKey)) {
            count("app.booking.lock.fail");
            return BookingResult.fail("当前预约人数较多，请稍后重试");
        }

        try {
            Appointment appointmentDB = getOne(appointment);
            if (appointmentDB != null) {
                count("app.booking.duplicate");
                return BookingResult.fail("您在相同科室和时段已有预约，请勿重复预约");
            }

            boolean hasSlot = hasAvailableSlot(
                    appointment.getDepartment(),
                    appointment.getDate(),
                    appointment.getTime(),
                    appointment.getDoctorName()
            );
            if (!hasSlot) {
                count("app.booking.capacity.full");
                return BookingResult.fail("当前时段号源已满，请更换日期、时段或医生");
            }

            appointment.setId(null);
            boolean saved = save(appointment);
            count(saved ? "app.booking.success" : "app.booking.fail");
            if (saved) {
                publishAfterCommit("BOOKED", appointment);
            }
            return saved
                    ? BookingResult.ok("预约成功，请按时就诊并携带有效证件")
                    : BookingResult.fail("预约失败，请稍后重试");
        } finally {
            distributedLockClient.unlock(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult reschedule(Appointment oldAppointment, Appointment newAppointment) {
        Appointment oldDb = getOne(oldAppointment);
        if (oldDb == null) {
            count("app.booking.reschedule.old.not_found");
            return BookingResult.fail("原预约不存在");
        }

        if (isSameSlot(oldAppointment, newAppointment)) {
            count("app.booking.reschedule.noop");
            return BookingResult.ok("新旧时段一致，无需改约");
        }

        String oldLockKey = buildSlotLockKey(oldAppointment);
        String newLockKey = buildSlotLockKey(newAppointment);
        List<String> lockKeys = sortLockKeys(oldLockKey, newLockKey);
        List<String> acquired = new ArrayList<>(2);

        try {
            for (String key : lockKeys) {
                if (!tryAcquireLock(key)) {
                    count("app.booking.reschedule.lock.fail");
                    return BookingResult.fail("当前改约请求较多，请稍后重试");
                }
                acquired.add(key);
            }

            Appointment duplicated = getOne(newAppointment);
            if (duplicated != null) {
                count("app.booking.reschedule.duplicate");
                return BookingResult.fail("您在目标时段已有预约，请选择其他时段");
            }

            boolean hasSlot = hasAvailableSlot(
                    newAppointment.getDepartment(),
                    newAppointment.getDate(),
                    newAppointment.getTime(),
                    newAppointment.getDoctorName()
            );
            if (!hasSlot) {
                count("app.booking.reschedule.capacity.full");
                return BookingResult.fail("目标时段号源已满，请选择其他时段");
            }

            newAppointment.setId(null);
            boolean saved = save(newAppointment);
            if (!saved) {
                count("app.booking.reschedule.insert.fail");
                throw new IllegalStateException("改约失败：创建新预约失败");
            }

            boolean removed = removeById(oldDb.getId());
            if (!removed) {
                count("app.booking.reschedule.remove.fail");
                throw new IllegalStateException("改约失败：取消原预约失败");
            }

            count("app.booking.reschedule.success");
            publishAfterCommit("RESCHEDULED", newAppointment);
            return BookingResult.ok("改约成功");
        } finally {
            for (String key : acquired) {
                distributedLockClient.unlock(key);
            }
        }
    }

    private int resolveCapacity(String department) {
        if ("内科".equalsIgnoreCase(department)) {
            return neikeCapacity;
        }
        if ("儿科".equalsIgnoreCase(department)) {
            return erkeCapacity;
        }
        return defaultCapacity;
    }

    private String buildSlotLockKey(Appointment appointment) {
        String doctorName = StringUtils.hasText(appointment.getDoctorName()) ? appointment.getDoctorName().trim() : "*";
        return "appt:" + appointment.getDepartment() + ":" + appointment.getDate() + ":" + appointment.getTime() + ":" + doctorName;
    }

    private boolean tryAcquireLock(String lockKey) {
        return distributedLockClient.tryLock(lockKey, lockProperties.getWaitMs(), lockProperties.getLeaseMs());
    }

    private List<String> sortLockKeys(String first, String second) {
        List<String> keys = new ArrayList<>(2);
        keys.add(first);
        keys.add(second);
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    private boolean isSameSlot(Appointment oldAppointment, Appointment newAppointment) {
        return sameText(oldAppointment.getDepartment(), newAppointment.getDepartment())
                && oldAppointment.getDate().equals(newAppointment.getDate())
                && oldAppointment.getTime() == newAppointment.getTime()
                && sameText(normalizeDoctor(oldAppointment.getDoctorName()), normalizeDoctor(newAppointment.getDoctorName()));
    }

    private String normalizeDoctor(String doctorName) {
        if (!StringUtils.hasText(doctorName)) {
            return "*";
        }
        return doctorName.trim();
    }

    private boolean sameText(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void count(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }

    private void publishAfterCommit(String action, Appointment appointment) {
        if (bookingEventPublisher == null) {
            return;
        }
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setBizType("APPOINTMENT");
        event.setAction(action);
        event.setUsername(appointment.getUsername());
        event.setIdCard(appointment.getIdCard());
        event.setDepartment(appointment.getDepartment());
        event.setDate(appointment.getDate() == null ? null : appointment.getDate().toString());
        event.setTime(appointment.getTime() == null ? null : appointment.getTime().name());
        event.setDoctorName(appointment.getDoctorName());
        event.setOccurredAt(LocalDateTime.now().toString());
        bookingEventPublisher.publishAfterCommit(event);
    }
}
