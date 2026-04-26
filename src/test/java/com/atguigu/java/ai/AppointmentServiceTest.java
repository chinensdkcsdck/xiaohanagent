package com.atguigu.java.ai;

import com.atguigu.java.ai.langchain4j.XiaohanAPP;
import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.AppointmentPeriod;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@SpringBootTest(classes = XiaohanAPP.class)
class AppointmentServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void testGetOne() {
        Appointment appointment = new Appointment();
        appointment.setUsername("张三");
        appointment.setIdCard("123456789012345678");
        appointment.setDepartment("内科");
        appointment.setDate(LocalDate.parse("2026-04-14"));
        appointment.setTime(AppointmentPeriod.AM);

        Appointment appointmentDB = appointmentService.getOne(appointment);
        System.out.println(appointmentDB);
    }

    @Test
    void testSave() {
        Appointment appointment = new Appointment();
        appointment.setUsername("张三");
        appointment.setIdCard("123456789012345678");
        appointment.setDepartment("内科");
        appointment.setDate(LocalDate.parse("2026-04-14"));
        appointment.setTime(AppointmentPeriod.AM);
        appointment.setDoctorName("张医生");

        appointmentService.save(appointment);
    }

    @Test
    void testRemoveById() {
        appointmentService.removeById(1L);
    }

    @Test
    void testUploadKnowledgeLibrary() {
        Path base = Path.of("src", "test", "resources", "knowledge");
        Document document1 = FileSystemDocumentLoader.loadDocument(base.resolve("医院信息.md").toString());
        Document document2 = FileSystemDocumentLoader.loadDocument(base.resolve("科室信息.md").toString());
        Document document3 = FileSystemDocumentLoader.loadDocument(base.resolve("神经内科.md").toString());
        List<Document> documents = Arrays.asList(document1, document2, document3);

        EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build()
                .ingest(documents);
    }
}
