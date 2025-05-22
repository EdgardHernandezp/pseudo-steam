package com.dreamseeker.pseudo_steam.exceptions;

import lombok.Getter;

@Getter
public class BucketNameExistsException extends Exception {
    private final String bucketName;

    public BucketNameExistsException(String bucketName, Throwable cause) {
        super(bucketName, cause);
        this.bucketName = bucketName;
    }
}
