package com.finai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FinAI Application - 金融投研智能体系统
 *
 * 主要功能:
 * 1. 财务报告分析
 * 2. 自动化估值建模
 * 3. 证据溯源管理
 * 4. 规则验证
 * 5. 智能体编排
 *
 * @author FinAI Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class FinAIApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinAIApplication.class, args);
        System.out.println("""

            ╔════════════════════════════════════════════════════════════╗
            ║                                                            ║
            ║   ███████╗██╗███╗   ██╗ █████╗ ██╗                        ║
            ║   ██╔════╝██║████╗  ██║██╔══██╗██║                        ║
            ║   █████╗  ██║██╔██╗ ██║███████║██║                        ║
            ║   ██╔══╝  ██║██║╚██╗██║██╔══██║██║                        ║
            ║   ██║     ██║██║ ╚████║██║  ██║██║                        ║
            ║   ╚═╝     ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝                        ║
            ║                                                            ║
            ║   金融投研智能体系统 v1.0.0                                 ║
            ║   Financial Investment Research AI Agent System            ║
            ║                                                            ║
            ║   🚀 Backend server is running!                            ║
            ║   📊 API: http://localhost:8080                            ║
            ║   📖 Docs: http://localhost:8080/swagger-ui.html           ║
            ║   💾 H2 Console: http://localhost:8080/h2-console          ║
            ║                                                            ║
            ╚════════════════════════════════════════════════════════════╝
            """);
    }
}
