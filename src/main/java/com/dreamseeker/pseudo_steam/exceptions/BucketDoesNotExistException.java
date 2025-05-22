package com.dreamseeker.pseudo_steam.exceptions;

import lombok.Getter;

@Getter
public class BucketDoesNotExistException extends Exception {
    private final String bucketName;

    public BucketDoesNotExistException(String bucketName, Throwable cause) {
        super(bucketName, cause);
        this.bucketName = bucketName;
    }
}
