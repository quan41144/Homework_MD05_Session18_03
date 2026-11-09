package com.storex.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.sql.SQLException;

public class DiscordAppenderDemo {

    private static final Logger log = LoggerFactory.getLogger(DiscordAppenderDemo.class);

    public static void main(String[] args) throws InterruptedException {
        MDC.put("requestId", "req-89a712f5-db-err");

        log.info("Khởi động ứng dụng kiểm tra hệ thống cảnh báo Discord Appender...");
        log.info("Kết nối cơ sở dữ liệu và các dịch vụ microservice...");

        log.warn("Cảnh báo: Thời gian phản hồi DB vượt ngưỡng 200ms");

        System.out.println("\n>>> [TEST 1] Thực thi lệnh log.error(\"Lỗi DB\") <<<");
        log.error("Lỗi DB");

        Thread.sleep(1000);

        System.out.println("\n>>> [TEST 2] Thực thi lệnh log.error kèm Exception SQLException <<<");
        try {
            simulateDatabaseQueryFailure();
        } catch (SQLException e) {
            log.error("Lỗi DB - Không thể kết nối tới máy chủ Database PostgreSQL: Connection refused (port 5432)", e);
        }

        Thread.sleep(2000);

        MDC.clear();
        log.info("Hoàn tất kiểm thử DiscordAppender!");
    }

    private static void simulateDatabaseQueryFailure() throws SQLException {
        throw new SQLException("SQLState 08001: Connection refused. Check host and port configuration.");
    }
}
