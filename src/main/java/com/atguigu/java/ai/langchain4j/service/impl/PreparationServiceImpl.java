package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.VisitPreparation;
import com.atguigu.java.ai.langchain4j.mapper.VisitPreparationMapper;
import com.atguigu.java.ai.langchain4j.service.KnowledgeIngestionService;
import com.atguigu.java.ai.langchain4j.service.PreparationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PreparationServiceImpl implements PreparationService {

    @Autowired
    private VisitPreparationMapper visitPreparationMapper;

    @Autowired
    private KnowledgeIngestionService knowledgeIngestionService;

    @Override
    public String queryPreparationGuide(String department, String examType) {
        LambdaQueryWrapper<VisitPreparation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitPreparation::getEnabled, 1);
        if (StringUtils.hasText(department)) {
            wrapper.eq(VisitPreparation::getDepartment, department.trim());
        }
        if (StringUtils.hasText(examType)) {
            wrapper.eq(VisitPreparation::getExamType, examType.trim());
        }
        wrapper.last("LIMIT 1");
        VisitPreparation guide = visitPreparationMapper.selectOne(wrapper);
        if (guide == null) {
            return "暂未查询到对应的就诊前准备说明，请联系人工客服确认。";
        }

        StringBuilder sb = new StringBuilder("就诊前准备：").append(guide.getPrepContent());
        if (StringUtils.hasText(guide.getRiskNotice())) {
            sb.append("；风险提示：").append(guide.getRiskNotice());
        }
        return sb.toString();
    }

    @Override
    public Long upsertPreparationGuide(String department, String examType, String prepContent, String riskNotice) {
        LambdaQueryWrapper<VisitPreparation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitPreparation::getDepartment, department)
                .eq(VisitPreparation::getExamType, examType)
                .last("LIMIT 1");
        VisitPreparation existing = visitPreparationMapper.selectOne(wrapper);
        if (existing == null) {
            VisitPreparation add = new VisitPreparation();
            add.setDepartment(department);
            add.setExamType(examType);
            add.setPrepContent(prepContent);
            add.setRiskNotice(riskNotice);
            add.setEnabled(1);
            visitPreparationMapper.insert(add);
            knowledgeIngestionService.ingestPreparationGuide(add);
            return add.getId();
        }

        existing.setPrepContent(prepContent);
        existing.setRiskNotice(riskNotice);
        existing.setEnabled(1);
        visitPreparationMapper.updateById(existing);
        knowledgeIngestionService.ingestPreparationGuide(existing);
        return existing.getId();
    }

    @Override
    public List<VisitPreparation> listEnabledGuides() {
        LambdaQueryWrapper<VisitPreparation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitPreparation::getEnabled, 1);
        return visitPreparationMapper.selectList(wrapper);
    }
}
