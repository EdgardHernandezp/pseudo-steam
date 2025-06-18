package com.dreamseeker.pseudo_steam.domains;

public record InitiateUploadRequest(String gameName, long fileSize, String contentType) {
}
