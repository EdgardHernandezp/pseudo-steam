package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AWSObjectStorageClientTest {
    @Autowired
    private AWSObjectStorageClient awsObjectStorageClient;

    private String studioId;

    @Test
    @Order(1)
    void createBucketStudio() throws BucketNameExistsException {
        BucketsPage.Bucket studioBucket = awsObjectStorageClient.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();
        assertThat(studioBucket).isNotNull();
        assertThat(studioBucket.bucketName()).isNotNull().isNotBlank();
    }

    @Test
    @Order(2)
    void putObjectSuccessfully() throws IOException, BucketDoesNotExistException {
        Resource resource = new ClassPathResource("file_1mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);
        String gameName = "gta-6";
        ObjectUploadResponse response = awsObjectStorageClient.putObject(studioId, gameName, multipartFile);

        assertThat(response).isNotNull();
        assertThat(response.gameName()).isEqualTo(gameName);
        assertThat(response.studioId()).isEqualTo(studioId);
        assertThat(response.versionId()).isNotNull();
    }

    @Test
    @Disabled
    void deleteBucket() {

    }
}