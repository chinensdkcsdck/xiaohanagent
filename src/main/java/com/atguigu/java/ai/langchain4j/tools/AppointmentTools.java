package com.atguigu.java.ai.langchain4j.tools;

import com.atguigu.java.ai.langchain4j.auth.LoginUser;
import com.atguigu.java.ai.langchain4j.auth.UserContextHolder;
import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.ExamBookingRequest;
import com.atguigu.java.ai.langchain4j.service.ExamService;
import com.atguigu.java.ai.langchain4j.service.FollowUpService;
import com.atguigu.java.ai.langchain4j.service.KnowledgeIngestionService;
import com.atguigu.java.ai.langchain4j.service.PreparationService;
import com.atguigu.java.ai.langchain4j.service.WaitlistService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component("AppointmentTools")
public class AppointmentTools {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private WaitlistService waitlistService;
    @Autowired
    private PreparationService preparationService;
    @Autowired
    private ExamService examService;
    @Autowired
    private FollowUpService followUpService;
    @Autowired
    private KnowledgeIngestionService knowledgeIngestionService;

    @Tool(name = "预约挂号", value = "根据用户信息预约门诊号源。")
    public String bookAppointment(Appointment appointment) {
        fillAppointmentUserContext(appointment);
        String validation = validateAppointment(appointment);
        if (validation != null) {
            return validation;
        }
        return appointmentService.tryBook(appointment).message();
    }

    @Tool(name = "取消预约挂号", value = "根据用户信息取消已有挂号预约。")
    public String cancelAppointment(Appointment appointment) {
        fillAppointmentUserContext(appointment);
        String validation = validateAppointment(appointment);
        if (validation != null) {
            return validation;
        }
        Appointment appointmentDB = appointmentService.getOne(appointment);
        if (appointmentDB == null) {
            return "未找到匹配的预约记录，请核对科室、日期和时段。";
        }
        return appointmentService.removeById(appointmentDB.getId())
                ? "取消预约成功。"
                : "取消预约失败，请稍后重试。";
    }

    @Tool(name = "改约挂号", value = "将原预约改到新日期和时段，成功后自动取消原预约。")
    public String rescheduleAppointment(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("科室") String department,
            @P("原预约日期，格式 yyyy-MM-dd") String oldDate,
            @P("原预约时段，上午或下午") String oldTime,
            @P("新预约日期，格式 yyyy-MM-dd") String newDate,
            @P("新预约时段，上午或下午") String newTime,
            @P(value = "医生姓名，可选", required = false) String doctorName
    ) {
        try {
            String resolvedUsername = resolveUsername(username);
            String resolvedIdCard = resolveIdCard(idCard);

            Appointment source = new Appointment();
            source.setUsername(resolvedUsername);
            source.setIdCard(resolvedIdCard);
            source.setDepartment(department);
            source.setDate(LocalDate.parse(oldDate.trim()));
            source.setTime(AppointmentPeriod.from(oldTime.trim()));
            source.setDoctorName(doctorName);
            Appointment oldBooking = appointmentService.getOne(source);
            if (oldBooking == null) {
                return "未找到原预约记录，无法改约。";
            }

            Appointment target = new Appointment();
            target.setUsername(resolvedUsername);
            target.setIdCard(resolvedIdCard);
            target.setDepartment(department);
            target.setDate(LocalDate.parse(newDate.trim()));
            target.setTime(AppointmentPeriod.from(newTime.trim()));
            target.setDoctorName(doctorName);
            String validation = validateAppointment(target);
            if (validation != null) {
                return validation;
            }

            BookingResult result = appointmentService.tryBook(target);
            if (!result.success()) {
                return "改约失败：" + result.message();
            }

            boolean removed = appointmentService.removeById(oldBooking.getId());
            if (!removed) {
                return "新预约已成功，但原预约取消失败，请人工核对。";
            }
            return "改约成功，已为您保留新时段。";
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "改约参数格式错误，请检查日期和时段（上午/下午）。";
        }
    }

    @Tool(name = "门诊候补登记", value = "当门诊号源不足时登记候补请求。")
    public String createAppointmentWaitlist(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("科室") String department,
            @P("预约日期，格式 yyyy-MM-dd") String date,
            @P("时段，上午或下午") String time,
            @P(value = "医生姓名，可选", required = false) String doctorName,
            @P(value = "候补备注，可选", required = false) String note
    ) {
        try {
            Long waitId = waitlistService.createAppointmentWaitlist(
                    resolveUsername(username),
                    resolveIdCard(idCard),
                    department,
                    LocalDate.parse(date.trim()),
                    AppointmentPeriod.from(time.trim()),
                    doctorName,
                    note
            );
            return "候补登记成功，候补单号：" + waitId + "。";
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "候补登记失败：日期或时段格式错误。";
        }
    }

    @Tool(name = "查询门诊号源", value = "根据科室、日期、时段、医生查询门诊号源是否可约。")
    public boolean queryDepartment(
            @P("科室名称") String name,
            @P("日期，格式 yyyy-MM-dd") String date,
            @P("时间，可选值：上午、下午") String time,
            @P(value = "医生姓名，可选", required = false) String doctorName
    ) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(date) || !StringUtils.hasText(time)) {
            return false;
        }
        try {
            return appointmentService.hasAvailableSlot(
                    name.trim(),
                    LocalDate.parse(date.trim()),
                    AppointmentPeriod.from(time.trim()),
                    doctorName
            );
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Tool(name = "查询就诊前准备", value = "按科室或检查项目返回就诊前准备事项。")
    public String queryVisitPreparation(
            @P(value = "科室，可选", required = false) String department,
            @P(value = "检查项目，可选", required = false) String examType
    ) {
        if (!StringUtils.hasText(department) && !StringUtils.hasText(examType)) {
            return "请至少提供科室或检查项目中的一个。";
        }
        return preparationService.queryPreparationGuide(department, examType);
    }

    @Tool(name = "维护就诊前准备", value = "维护科室或检查项目的就诊前准备内容，保存后自动同步向量库。")
    public String upsertVisitPreparation(
            @P("科室") String department,
            @P("检查项目") String examType,
            @P("准备内容") String prepContent,
            @P(value = "风险提示，可选", required = false) String riskNotice
    ) {
        if (!StringUtils.hasText(department) || !StringUtils.hasText(examType) || !StringUtils.hasText(prepContent)) {
            return "科室、检查项目、准备内容都不能为空。";
        }
        Long id = preparationService.upsertPreparationGuide(department.trim(), examType.trim(), prepContent.trim(), riskNotice);
        return "就诊前准备已保存并同步向量库，记录ID：" + id;
    }

    @Tool(name = "预约检查检验", value = "预约检查检验时段，支持医生维度。")
    public String bookExam(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("检查项目") String examType,
            @P("日期，格式 yyyy-MM-dd") String date,
            @P("时段，上午或下午") String time,
            @P(value = "医生姓名，可选", required = false) String doctorName
    ) {
        try {
            return examService.tryBookExam(
                    buildExamRequest(resolveUsername(username), resolveIdCard(idCard), examType, date, time, doctorName)
            ).message();
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "检查预约失败：日期或时段格式错误。";
        }
    }

    @Tool(name = "取消检查检验预约", value = "取消已预约的检查检验时段。")
    public String cancelExam(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("检查项目") String examType,
            @P("日期，格式 yyyy-MM-dd") String date,
            @P("时段，上午或下午") String time,
            @P(value = "医生姓名，可选", required = false) String doctorName
    ) {
        try {
            return examService.cancelExamBooking(
                    buildExamRequest(resolveUsername(username), resolveIdCard(idCard), examType, date, time, doctorName)
            ).message();
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "取消检查预约失败：日期或时段格式错误。";
        }
    }

    @Tool(name = "检查候补登记", value = "检查检验时段已满时进行候补登记。")
    public String createExamWaitlist(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("检查项目") String examType,
            @P("日期，格式 yyyy-MM-dd") String date,
            @P("时段，上午或下午") String time,
            @P(value = "医生姓名，可选", required = false) String doctorName,
            @P(value = "候补备注，可选", required = false) String note
    ) {
        try {
            Long waitId = waitlistService.createExamWaitlist(
                    resolveUsername(username),
                    resolveIdCard(idCard),
                    examType,
                    LocalDate.parse(date.trim()),
                    AppointmentPeriod.from(time.trim()),
                    doctorName,
                    note
            );
            return "检查候补登记成功，候补单号：" + waitId + "。";
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "检查候补登记失败：日期或时段格式错误。";
        }
    }

    @Tool(name = "创建复诊计划", value = "根据患者信息创建复诊计划。")
    public String createFollowUpPlan(
            @P(value = "姓名", required = false) String username,
            @P(value = "身份证号", required = false) String idCard,
            @P("复诊科室") String department,
            @P("建议复诊日期，格式 yyyy-MM-dd") String date,
            @P("建议复诊时段，上午或下午") String time,
            @P(value = "复诊医生，可选", required = false) String doctorName,
            @P(value = "复诊原因，可选", required = false) String reason
    ) {
        try {
            Long planId = followUpService.createFollowUpPlan(
                    resolveUsername(username),
                    resolveIdCard(idCard),
                    department,
                    LocalDate.parse(date.trim()),
                    AppointmentPeriod.from(time.trim()),
                    doctorName,
                    reason
            );
            return "复诊计划创建成功，计划ID：" + planId + "。";
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return "复诊计划创建失败：日期或时段格式错误。";
        }
    }

    @Tool(name = "确认复诊预约", value = "将复诊计划转为正式挂号预约。")
    public String confirmFollowUpBooking(@P("复诊计划ID") Long planId) {
        if (planId == null) {
            return "复诊计划ID不能为空。";
        }
        return followUpService.confirmFollowUp(planId).message();
    }

    @Tool(name = "同步业务流程到知识库", value = "执行全量业务流程知识同步，写入向量库。")
    public String ingestWorkflowKnowledge() {
        int count = knowledgeIngestionService.ingestWorkflowKnowledge();
        return "业务流程知识入库完成，已写入 " + count + " 条文本片段。";
    }

    private ExamBookingRequest buildExamRequest(String username, String idCard, String examType, String date,
                                                String time, String doctorName) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(idCard) || !StringUtils.hasText(examType)) {
            throw new IllegalArgumentException("姓名、身份证号、检查项目不能为空");
        }
        ExamBookingRequest request = new ExamBookingRequest();
        request.setUsername(username.trim());
        request.setIdCard(idCard.trim());
        request.setExamType(examType.trim());
        request.setDate(LocalDate.parse(date.trim()));
        request.setTime(AppointmentPeriod.from(time.trim()));
        request.setDoctorName(doctorName);
        return request;
    }

    private String validateAppointment(Appointment appointment) {
        if (appointment == null) {
            return "预约参数为空，请补充预约信息。";
        }
        if (!StringUtils.hasText(appointment.getUsername())) {
            return "缺少姓名，请补充后再试。";
        }
        if (!StringUtils.hasText(appointment.getIdCard())) {
            return "缺少身份证号，请补充后再试。";
        }
        if (!StringUtils.hasText(appointment.getDepartment())) {
            return "缺少预约科室，请补充后再试。";
        }
        if (appointment.getDate() == null) {
            return "缺少预约日期，请补充后再试。";
        }
        if (appointment.getTime() == null) {
            return "缺少预约时段，请补充后再试。";
        }
        if (appointment.getDate().isBefore(LocalDate.now())) {
            return "预约日期不能早于今天，请重新选择日期。";
        }
        return null;
    }

    private void fillAppointmentUserContext(Appointment appointment) {
        if (appointment == null) {
            return;
        }
        if (!StringUtils.hasText(appointment.getUsername())) {
            appointment.setUsername(resolveUsername(null));
        }
        if (!StringUtils.hasText(appointment.getIdCard())) {
            appointment.setIdCard(resolveIdCard(null));
        }
    }

    private String resolveUsername(String username) {
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        LoginUser loginUser = UserContextHolder.get();
        if (loginUser != null && StringUtils.hasText(loginUser.username())) {
            return loginUser.username().trim();
        }
        throw new IllegalArgumentException("缺少姓名，请先登录后再试");
    }

    private String resolveIdCard(String idCard) {
        if (StringUtils.hasText(idCard)) {
            return idCard.trim();
        }
        LoginUser loginUser = UserContextHolder.get();
        if (loginUser != null && StringUtils.hasText(loginUser.idCard())) {
            return loginUser.idCard().trim();
        }
        throw new IllegalArgumentException("缺少身份证号，请先登录后再试");
    }
}