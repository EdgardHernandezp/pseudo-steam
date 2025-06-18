package com.dreamseeker.pseudo_steam.domains;

import java.util.List;

public record CompleteUploadRequest(String uploadId, String key, List<CompletedPart> parts) {
    public record CompletedPart(int partNumber, String etag) {
    }
}
