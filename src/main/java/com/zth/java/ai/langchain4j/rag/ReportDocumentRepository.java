package com.zth.java.ai.langchain4j.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReportDocumentRepository extends JpaRepository<ReportDocument, String> {
    Optional<ReportDocument> findByIdAndUserId(String id, Long userId);
}
