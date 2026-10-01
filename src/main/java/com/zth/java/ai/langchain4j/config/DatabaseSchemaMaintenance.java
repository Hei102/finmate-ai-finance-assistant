package com.zth.java.ai.langchain4j.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 启动时兼容旧版收藏表的索引结构，确保收藏按用户区分。 */
@Component
@RequiredArgsConstructor
public class DatabaseSchemaMaintenance implements ApplicationRunner {

    private static final String FAVORITE_TABLE = "favorite_assets";
    private static final String OLD_FAVORITE_INDEX = "uk_favorite_assets_symbol_category";
    private static final String USER_SCOPED_FAVORITE_INDEX = "uk_favorite_assets_user_symbol_category";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(FAVORITE_TABLE)) {
            return;
        }
        // 早期版本把收藏唯一性做成了“代码 + 分类”全局唯一；多用户系统里必须改成按 user_id 隔离。
        dropIndexIfExists(FAVORITE_TABLE, OLD_FAVORITE_INDEX);
        addFavoriteUserScopedIndexIfAbsent();
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        if (indexExists(tableName, indexName)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        }
    }

    private void addFavoriteUserScopedIndexIfAbsent() {
        if (!indexExists(FAVORITE_TABLE, USER_SCOPED_FAVORITE_INDEX)) {
            jdbcTemplate.execute("ALTER TABLE favorite_assets ADD CONSTRAINT "
                    + USER_SCOPED_FAVORITE_INDEX
                    + " UNIQUE (user_id, symbol, category)");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }
}
