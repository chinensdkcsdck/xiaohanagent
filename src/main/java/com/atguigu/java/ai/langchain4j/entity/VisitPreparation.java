package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("visit_preparation")
public class VisitPreparation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String department;
    private String examType;
    private String prepContent;
    private String riskNotice;
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
    }

    public String getPrepContent() {
        return prepContent;
    }

    public void setPrepContent(String prepContent) {
        this.prepContent = prepContent;
    }

    public String getRiskNotice() {
        return riskNotice;
    }

    public void setRiskNotice(String riskNotice) {
        this.riskNotice = riskNotice;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
