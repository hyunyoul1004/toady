package com.toady.web.app.announcement.document;

import java.net.URI;

public interface NoticeDocumentTransport {
    byte[] get(URI uri) throws Exception;
}
