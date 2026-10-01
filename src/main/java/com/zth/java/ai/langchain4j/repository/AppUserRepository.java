package com.zth.java.ai.langchain4j.repository;

import com.zth.java.ai.langchain4j.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户数据访问：由 Spring Data JPA 按方法名生成账号查询。 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
