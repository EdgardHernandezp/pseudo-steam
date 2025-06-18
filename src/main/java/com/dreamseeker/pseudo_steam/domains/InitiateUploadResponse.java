package com.dreamseeker.pseudo_steam.domains;

import java.util.List;

public record InitiateUploadResponse(String uploadId, String gameName, List<PreSignedPartUrl> presignedUrls) {
}
