package com.zth.java.ai.langchain4j.repository;

import com.zth.java.ai.langchain4j.entity.FinanceChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 会话目录数据访问：查询用户会话，并校验会话归属。 */
public interface FinanceChatSessionRepository extends JpaRepository<FinanceChatSession, Long> {

    List<FinanceChatSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<FinanceChatSession> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
