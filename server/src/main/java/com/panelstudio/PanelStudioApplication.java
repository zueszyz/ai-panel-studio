package com.panelstudio;

import com.panelstudio.infra.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用入口。启动前确保 SQLite 数据目录存在（SQLite 不会创建缺失的父目录）。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
@MapperScan("com.panelstudio.infra.mapper")
public class PanelStudioApplication {

    public static void main(String[] args) {
        ensureDataDirectory();
        SpringApplication.run(PanelStudioApplication.class, args);
    }

    private static void ensureDataDirectory() {
        String raw = System.getenv().getOrDefault("SQLITE_PATH", "./data/panel-studio.db");
        Path parent = Paths.get(raw).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建数据库目录: " + parent, e);
        }
    }
}
