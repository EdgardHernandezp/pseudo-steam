package com.dreamseeker.pseudo_steam.domains;

public record PreSignedPartUrl(int partNumber, String preSignedUrl, long partSize) {
}
