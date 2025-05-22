package com.dreamseeker.pseudo_steam.controllers.advices;

import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(BucketDoesNotExistException.class)
    public ResponseEntity<String> handleBucketDoesNotExistException(BucketDoesNotExistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bucket does not exist: " + ex.getBucketName());
    }

    @ExceptionHandler(BucketNameExistsException.class)
    public ResponseEntity<String> handleBucketNameExistsException(BucketNameExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Bucket name already exists: " + ex.getBucketName());
    }

    @ExceptionHandler(BucketNotEmptyException.class)
    public ResponseEntity<String> handleBucketNotEmptyException(BucketNotEmptyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Bucket is not empty: " + ex.getBucketName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + ex.getMessage());
    }
}
