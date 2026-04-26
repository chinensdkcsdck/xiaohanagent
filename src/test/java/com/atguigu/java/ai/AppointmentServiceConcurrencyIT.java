package com.atguigu.java.ai;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.impl.AppointmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AppointmentServiceConcurrencyIT.TestApp.class,
        properties = {
                "app.appointment.default-capacity=5",
                "app.appointment.department-capacity.neike=5",
                "app.appointment.department-capacity.erke=5"
        }
)
class AppointmentServiceConcurrencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("xiaohan")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS appointment");
        jdbcTemplate.execute("""
                CREATE TABLE appointment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(64) NOT NULL,
                    id_card VARCHAR(32) NOT NULL,
                    department VARCHAR(64) NOT NULL,
                    date DATE NOT NULL,
                    time VARCHAR(16) NOT NULL,
                    doctor_name VARCHAR(64) NULL
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE appointment
                ADD UNIQUE INDEX uk_appointment_user_slot (username, id_card, department, date, time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_appointment_slot_doctor
                ON appointment (department, date, time, doctor_name)
                """);
    }

    @Test
    void shouldLimitCapacityUnderConcurrency() throws Exception {
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<BookingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int n = i;
                tasks.add(() -> appointmentService.tryBook(buildAppointment("用户" + n, "31010119900101" + String.format("%04d", n))));
            }

            List<Future<BookingResult>> futures = pool.invokeAll(tasks);
            int successCount = 0;
            for (Future<BookingResult> future : futures) {
                BookingResult result = future.get();
                if (result.success()) {
                    successCount++;
                }
            }

            Long saved = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment", Long.class);
            assertEquals(5, successCount);
            assertEquals(5L, saved);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldPreventDuplicateBookingForSameUserAndSlot() {
        Appointment appointment = buildAppointment("张三", "310101199001010001");
        BookingResult first = appointmentService.tryBook(appointment);
        BookingResult second = appointmentService.tryBook(appointment);

        assertTrue(first.success());
        assertTrue(!second.success());
        Long saved = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment", Long.class);
        assertEquals(1L, saved);
    }

    private Appointment buildAppointment(String username, String idCard) {
        Appointment appointment = new Appointment();
        appointment.setUsername(username);
        appointment.setIdCard(idCard);
        appointment.setDepartment("内科");
        appointment.setDate(LocalDate.now().plusDays(1));
        appointment.setTime(AppointmentPeriod.AM);
        appointment.setDoctorName("王医生");
        return appointment;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
    @MapperScan("com.atguigu.java.ai.langchain4j.mapper")
    @ComponentScan(basePackageClasses = AppointmentServiceImpl.class)
    static class TestApp {
    }
}
