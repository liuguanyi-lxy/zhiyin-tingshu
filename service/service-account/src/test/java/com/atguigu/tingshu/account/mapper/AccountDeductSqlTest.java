package com.atguigu.tingshu.account.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AccountDeductSqlTest {
    private String sql(String statement) throws Exception {
        MybatisConfiguration config = new MybatisConfiguration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/UserAccountMapper.xml")) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, "mapper/UserAccountMapper.xml", config.getSqlFragments()).parse();
        }
        return config.getMappedStatement(UserAccountMapper.class.getName() + "." + statement)
                .getBoundSql(Map.of("amount", new BigDecimal("-1.00"), "userId", 8L))
                .getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    void sqlRejectsNonPositiveAmountsEvenIfServiceValidationIsBypassed() throws Exception {
        String sql = sql("checkAndDeduct");
        assertTrue(sql.contains("AND ? > 0"));
        assertTrue(sql.contains("available_amount >= ?"));
    }

    @Test
    void deductionReadsAccountWithDatabaseRowLock() throws Exception {
        assertTrue(sql("selectForDeduction").endsWith("FOR UPDATE"));
    }
}
