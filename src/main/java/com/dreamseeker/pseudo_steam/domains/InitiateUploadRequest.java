package com.dreamseeker.pseudo_steam.domains;

import java.util.Map;

public record InitiateUploadRequest(String gameName, long fileSize, String contentType, Map<String, String> metadata) {
}
