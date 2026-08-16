package com.ntg.citizenlink.support;

import org.springframework.mock.web.MockMultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link MultipartFile} that records whether the stream handed out by
 * {@link #getInputStream()} was closed, so tests can verify upload handlers
 * do not leak file descriptors.
 */
public class CloseTrackingMultipartFile extends MockMultipartFile {

    private boolean closed;

    public CloseTrackingMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        super(name, originalFilename, contentType, content);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FilterInputStream(super.getInputStream()) {
            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
        };
    }

    public boolean isClosed() {
        return closed;
    }
}