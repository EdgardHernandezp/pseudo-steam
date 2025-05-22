package com.dreamseeker.pseudo_steam.exceptions;

import lombok.Getter;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Getter
public class BucketNotEmptyException extends Exception {
    private final String bucketName;

    public BucketNotEmptyException(String bucketName, AwsServiceException e) {
        super(bucketName, e);
        this.bucketName = bucketName;
    }
}
