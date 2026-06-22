package com.ploybot.promptshield.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

public class EmailTools {
    private static final Logger log = LoggerFactory.getLogger(EmailTools.class);

    @Tool(description = """
            Simulates sending an email.
            This tool NEVER sends real emails.
            It only logs the email contents for testing purposes.
            """)
    public String sendEmail(
            String to,
            String subject,
            String body) {

        log.info("""
                
                ================= EMAIL SIMULATION =================
                TO      : {}
                SUBJECT : {}
                BODY    :
                {}
                ===================================================
                """,
                to,
                subject,
                body);

        return "Email simulation completed successfully. Email was NOT sent.";
    }
}