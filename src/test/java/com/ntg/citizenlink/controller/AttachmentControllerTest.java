package com.ntg.citizenlink.controller;

import com.ntg.citizenlink.config.TestSecurityConfig;
import com.ntg.citizenlink.dto.agent.response.AttachmentResponse;
import com.ntg.citizenlink.security.JwtBlocklist;
import com.ntg.citizenlink.security.config.SecurityContextHelper;
import com.ntg.citizenlink.service.interfaces.AttachmentService;
import com.ntg.citizenlink.service.interfaces.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttachmentController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentService attachmentService;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlocklist jwtBlocklist;

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID ATTACHMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void downloadAttachment_setsWellFormedContentDisposition() throws Exception {
        AttachmentResponse attachment = attachment("report.pdf");
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(attachmentService.getAttachmentById(CASE_ID, ATTACHMENT_ID, USER_ID)).thenReturn(attachment);
        when(attachmentService.downloadAttachment(CASE_ID, ATTACHMENT_ID, USER_ID))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/v1/cases/{caseId}/attachments/{attachmentId}/download", CASE_ID, ATTACHMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"report.pdf\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''report.pdf")));
    }

    @Test
    void downloadAttachment_escapesQuoteInFilename() throws Exception {
        AttachmentResponse attachment = attachment("a\"b report.pdf");
        when(securityContextHelper.getAuthenticatedUserId()).thenReturn(USER_ID);
        when(attachmentService.getAttachmentById(CASE_ID, ATTACHMENT_ID, USER_ID)).thenReturn(attachment);
        when(attachmentService.downloadAttachment(CASE_ID, ATTACHMENT_ID, USER_ID))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        String contentDisposition = mockMvc.perform(get(
                "/api/v1/cases/{caseId}/attachments/{attachmentId}/download", CASE_ID, ATTACHMENT_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(contentDisposition)
                .isNotNull()
                .contains("filename*=UTF-8''a%22b%20report.pdf")
                .doesNotContain("filename=\"a\"b")
                .doesNotContain("\r")
                .doesNotContain("\n");
    }

    private AttachmentResponse attachment(String name) {
        return AttachmentResponse.builder()
                .originalFileName(name)
                .mimeType(MediaType.APPLICATION_PDF_VALUE)
                .fileSizeBytes(3L)
                .build();
    }
}
