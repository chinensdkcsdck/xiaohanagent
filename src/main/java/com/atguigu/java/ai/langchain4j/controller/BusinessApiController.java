package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.auth.AuthConstants;
import com.atguigu.java.ai.langchain4j.auth.LoginUser;
import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.exception.BusinessException;
import com.atguigu.java.ai.langchain4j.exception.ErrorCode;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.ChronicCareService;
import com.atguigu.java.ai.langchain4j.service.CostEstimateService;
import com.atguigu.java.ai.langchain4j.service.ExamBookingRequest;
import com.atguigu.java.ai.langchain4j.service.ExamJourneyService;
import com.atguigu.java.ai.langchain4j.service.ExamService;
import com.atguigu.java.ai.langchain4j.service.FollowUpService;
import com.atguigu.java.ai.langchain4j.service.KnowledgeIngestionService;
import com.atguigu.java.ai.langchain4j.service.MedicationSafetyService;
import com.atguigu.java.ai.langchain4j.service.OperationsDashboardService;
import com.atguigu.java.ai.langchain4j.service.PreparationService;
import com.atguigu.java.ai.langchain4j.service.ReportInterpretationService;
import com.atguigu.java.ai.langchain4j.service.TicketService;
import com.atguigu.java.ai.langchain4j.service.TriageService;
import com.atguigu.java.ai.langchain4j.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "业务接口")
@RestController
@RequestMapping("/api")
public class BusinessApiController {

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
    @Autowired
    private TriageService triageService;
    @Autowired
    private ExamJourneyService examJourneyService;
    @Autowired
    private ChronicCareService chronicCareService;
    @Autowired
    private CostEstimateService costEstimateService;
    @Autowired
    private MedicationSafetyService medicationSafetyService;
    @Autowired
    private ReportInterpretationService reportInterpretationService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private OperationsDashboardService operationsDashboardService;

    @Operation(summary = "预约挂号")
    @PostMapping("/appointments/book")
    public BookingResult bookAppointment(@RequestBody AppointmentRequest request, HttpSession session) {
        return appointmentService.tryBook(toAppointment(request, currentUser(session)));
    }

    @Operation(summary = "取消预约")
    @PostMapping("/appointments/cancel")
    public String cancelAppointment(@RequestBody AppointmentRequest request, HttpSession session) {
        Appointment appointment = toAppointment(request, currentUser(session));
        Appointment db = appointmentService.getOne(appointment);
        if (db == null) {
            return "未找到匹配预约记录";
        }
        return appointmentService.removeById(db.getId()) ? "取消成功" : "取消失败";
    }

    @Operation(summary = "改约")
    @PostMapping("/appointments/reschedule")
    public String reschedule(@RequestBody RescheduleRequest request, HttpSession session) {
        LoginUser user = currentUser(session);
        Appointment oldOne = toAppointment(new AppointmentRequest(
                request.department(), request.oldDate(), request.oldTime(), request.doctorName()
        ), user);
        Appointment target = toAppointment(new AppointmentRequest(
                request.department(), request.newDate(), request.newTime(), request.doctorName()
        ), user);
        BookingResult result = appointmentService.reschedule(oldOne, target);
        return result.success() ? result.message() : "改约失败: " + result.message();
    }

    @Operation(summary = "查询号源")
    @GetMapping("/appointments/availability")
    public boolean availability(
            @RequestParam String department,
            @RequestParam String date,
            @RequestParam String time,
            @RequestParam(required = false) String doctorName
    ) {
        if (!StringUtils.hasText(department)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "科室不能为空");
        }
        if (!StringUtils.hasText(date)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "日期不能为空，格式：yyyy-MM-dd");
        }
        if (!StringUtils.hasText(time)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "时段不能为空，支持：上午/下午");
        }
        return appointmentService.hasAvailableSlot(
                department.trim(),
                LocalDate.parse(date.trim()),
                AppointmentPeriod.from(time.trim()),
                StringUtils.hasText(doctorName) ? doctorName.trim() : null
        );
    }

    @Operation(summary = "门诊候补")
    @PostMapping("/waitlists/appointments")
    public Long appointmentWaitlist(@RequestBody WaitlistRequest request, HttpSession session) {
        LoginUser user = currentUser(session);
        return waitlistService.createAppointmentWaitlist(
                user.username(), user.idCard(), request.bizType(),
                LocalDate.parse(request.date()), AppointmentPeriod.from(request.time()),
                request.doctorName(), request.note()
        );
    }

    @Operation(summary = "检查候补")
    @PostMapping("/waitlists/exams")
    public Long examWaitlist(@RequestBody WaitlistRequest request, HttpSession session) {
        LoginUser user = currentUser(session);
        return waitlistService.createExamWaitlist(
                user.username(), user.idCard(), request.bizType(),
                LocalDate.parse(request.date()), AppointmentPeriod.from(request.time()),
                request.doctorName(), request.note()
        );
    }

    @Operation(summary = "查询就诊准备")
    @GetMapping("/preparations")
    public String queryPreparation(@RequestParam(required = false) String department,
                                   @RequestParam(required = false) String examType) {
        return preparationService.queryPreparationGuide(department, examType);
    }

    @Operation(summary = "维护就诊准备")
    @PostMapping("/preparations")
    public Long upsertPreparation(@RequestBody PreparationUpsertRequest request) {
        return preparationService.upsertPreparationGuide(
                request.department(), request.examType(), request.prepContent(), request.riskNotice()
        );
    }

    @Operation(summary = "知识全量同步")
    @PostMapping("/knowledge/ingest-workflow")
    public int ingestWorkflowKnowledge() {
        return knowledgeIngestionService.ingestWorkflowKnowledge();
    }

    @Operation(summary = "项目知识入库")
    @PostMapping("/knowledge/ingest-project-docs")
    public int ingestProjectKnowledge() {
        return knowledgeIngestionService.ingestProjectKnowledge();
    }

    @Operation(summary = "预约检查")
    @PostMapping("/exams/book")
    public BookingResult bookExam(@RequestBody ExamRequest request, HttpSession session) {
        return examService.tryBookExam(toExamRequest(request, currentUser(session)));
    }

    @Operation(summary = "取消检查预约")
    @PostMapping("/exams/cancel")
    public BookingResult cancelExam(@RequestBody ExamRequest request, HttpSession session) {
        return examService.cancelExamBooking(toExamRequest(request, currentUser(session)));
    }

    @Operation(summary = "创建复诊计划")
    @PostMapping("/followups/plans")
    public Long createFollowUp(@RequestBody FollowupPlanRequest request, HttpSession session) {
        LoginUser user = currentUser(session);
        return followUpService.createFollowUpPlan(
                user.username(), user.idCard(), request.department(),
                LocalDate.parse(request.date()), AppointmentPeriod.from(request.time()),
                request.doctorName(), request.reason()
        );
    }

    @Operation(summary = "确认复诊预约")
    @PostMapping("/followups/plans/{planId}/confirm")
    public BookingResult confirmFollowUp(@PathVariable Long planId) {
        return followUpService.confirmFollowUp(planId);
    }

    @Operation(summary = "AI分诊导诊")
    @PostMapping("/triage")
    public String triage(@RequestBody TriageRequest request) {
        return triageService.assess(request.symptoms(), request.age(), request.chronicDiseases());
    }

    @Operation(summary = "检查流程提醒")
    @PostMapping("/exam-journey/reminder")
    public String examJourney(@RequestBody ExamJourneyRequest request) {
        return examJourneyService.buildPreCheckReminder(request.examType(), request.appointmentTime());
    }

    @Operation(summary = "慢病随访计划")
    @PostMapping("/chronic/followup-plan")
    public String chronicPlan(@RequestBody ChronicPlanRequest request) {
        return chronicCareService.generateFollowupPlan(
                request.diseaseType(), request.latestIndicators(), request.medicationStatus()
        );
    }

    @Operation(summary = "费用与医保预估")
    @PostMapping("/cost/estimate")
    public String estimateCost(@RequestBody CostRequest request) {
        return costEstimateService.estimate(request.department(), request.examType(), request.insuranceType());
    }

    @Operation(summary = "用药安全评估")
    @PostMapping("/medication/safety")
    public String medicationSafety(@RequestBody MedicationSafetyRequest request) {
        return medicationSafetyService.check(request.medications(), request.allergies(), request.conditions());
    }

    @Operation(summary = "报告解读")
    @PostMapping("/reports/explain")
    public String explainReport(@RequestBody ReportRequest request) {
        return reportInterpretationService.explain(request.reportText());
    }

    @Operation(summary = "医患工单创建")
    @PostMapping("/tickets")
    public String createTicket(@RequestBody TicketRequest request, HttpSession session) {
        LoginUser user = currentUser(session);
        return ticketService.create(user.username(), request.category(), request.content(), request.priority());
    }

    @Operation(summary = "运营看板日报")
    @GetMapping("/operations/daily-summary")
    public String operationsSummary() {
        return operationsDashboardService.dailySummary();
    }

    private LoginUser currentUser(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(AuthConstants.SESSION_USER_KEY);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    private Appointment toAppointment(AppointmentRequest request, LoginUser user) {
        if (!StringUtils.hasText(request.department())
                || !StringUtils.hasText(request.date())
                || !StringUtils.hasText(request.time())) {
            throw new IllegalArgumentException("预约参数不完整");
        }
        Appointment appointment = new Appointment();
        appointment.setUsername(user.username());
        appointment.setIdCard(user.idCard());
        appointment.setDepartment(request.department().trim());
        appointment.setDate(LocalDate.parse(request.date().trim()));
        appointment.setTime(AppointmentPeriod.from(request.time().trim()));
        appointment.setDoctorName(request.doctorName());
        return appointment;
    }

    private ExamBookingRequest toExamRequest(ExamRequest request, LoginUser user) {
        if (!StringUtils.hasText(request.examType())
                || !StringUtils.hasText(request.date())
                || !StringUtils.hasText(request.time())) {
            throw new IllegalArgumentException("检查预约参数不完整");
        }
        ExamBookingRequest exam = new ExamBookingRequest();
        exam.setUsername(user.username());
        exam.setIdCard(user.idCard());
        exam.setExamType(request.examType().trim());
        exam.setDate(LocalDate.parse(request.date().trim()));
        exam.setTime(AppointmentPeriod.from(request.time().trim()));
        exam.setDoctorName(request.doctorName());
        return exam;
    }

    public record AppointmentRequest(String department, String date, String time, String doctorName) {
    }

    public record RescheduleRequest(
            String department, String oldDate, String oldTime, String newDate, String newTime, String doctorName
    ) {
    }

    public record WaitlistRequest(
            String bizType, String date, String time, String doctorName, String note
    ) {
    }

    public record PreparationUpsertRequest(
            String department, String examType, String prepContent, String riskNotice
    ) {
    }

    public record ExamRequest(
            String examType, String date, String time, String doctorName
    ) {
    }

    public record FollowupPlanRequest(
            String department, String date, String time, String doctorName, String reason
    ) {
    }

    public record TriageRequest(String symptoms, Integer age, String chronicDiseases) {
    }

    public record ExamJourneyRequest(String examType, String appointmentTime) {
    }

    public record ChronicPlanRequest(String diseaseType, String latestIndicators, String medicationStatus) {
    }

    public record CostRequest(String department, String examType, String insuranceType) {
    }

    public record MedicationSafetyRequest(String medications, String allergies, String conditions) {
    }

    public record ReportRequest(String reportText) {
    }

    public record TicketRequest(String category, String content, String priority) {
    }
}
