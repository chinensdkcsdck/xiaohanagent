package com.atguigu.java.ai.langchain4j.tools;

import com.atguigu.java.ai.langchain4j.service.ChronicCareService;
import com.atguigu.java.ai.langchain4j.service.CostEstimateService;
import com.atguigu.java.ai.langchain4j.service.ExamJourneyService;
import com.atguigu.java.ai.langchain4j.service.MedicationSafetyService;
import com.atguigu.java.ai.langchain4j.service.OperationsDashboardService;
import com.atguigu.java.ai.langchain4j.service.ReportInterpretationService;
import com.atguigu.java.ai.langchain4j.service.TicketService;
import com.atguigu.java.ai.langchain4j.service.TriageService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("AdvancedMedicalTools")
public class AdvancedMedicalTools {

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

    @Tool(name = "AI分诊导诊", value = "根据症状给出风险分级和科室建议。")
    public String triageGuide(
            @P("症状描述") String symptoms,
            @P(value = "年龄，可选", required = false) Integer age,
            @P(value = "慢病史，可选", required = false) String chronicDiseases
    ) {
        return triageService.assess(symptoms, age, chronicDiseases);
    }

    @Tool(name = "检查检验流程提醒", value = "生成检查前准备和到院提醒。")
    public String examJourneyReminder(
            @P("检查项目") String examType,
            @P(value = "预约时间，可选", required = false) String appointmentTime
    ) {
        return examJourneyService.buildPreCheckReminder(examType, appointmentTime);
    }

    @Tool(name = "慢病随访计划", value = "按病种生成慢病随访计划。")
    public String chronicDiseaseFollowup(
            @P("慢病类型") String diseaseType,
            @P(value = "最新指标，可选", required = false) String latestIndicators,
            @P(value = "用药情况，可选", required = false) String medicationStatus
    ) {
        return chronicCareService.generateFollowupPlan(diseaseType, latestIndicators, medicationStatus);
    }

    @Tool(name = "费用与医保预估", value = "根据科室/检查项目/医保类型返回预估费用。")
    public String estimateCost(
            @P(value = "科室，可选", required = false) String department,
            @P(value = "检查项目，可选", required = false) String examType,
            @P(value = "医保类型，可选", required = false) String insuranceType
    ) {
        return costEstimateService.estimate(department, examType, insuranceType);
    }

    @Tool(name = "用药安全评估", value = "检查药物组合的潜在风险。")
    public String medicationSafety(
            @P("当前用药") String medications,
            @P(value = "过敏史，可选", required = false) String allergies,
            @P(value = "既往病史，可选", required = false) String conditions
    ) {
        return medicationSafetyService.check(medications, allergies, conditions);
    }

    @Tool(name = "报告解读", value = "将检验/影像报告转换为通俗说明。")
    public String explainMedicalReport(@P("报告原文") String reportText) {
        return reportInterpretationService.explain(reportText);
    }

    @Tool(name = "医患工单", value = "创建医患沟通工单并返回工单号。")
    public String createConsultationTicket(
            @P("用户名") String username,
            @P("工单类别") String category,
            @P("工单内容") String content,
            @P(value = "优先级，可选", required = false) String priority
    ) {
        return ticketService.create(username, category, content, priority);
    }

    @Tool(name = "运营看板", value = "返回运营指标日报摘要。")
    public String operationsDashboard() {
        return operationsDashboardService.dailySummary();
    }
}

