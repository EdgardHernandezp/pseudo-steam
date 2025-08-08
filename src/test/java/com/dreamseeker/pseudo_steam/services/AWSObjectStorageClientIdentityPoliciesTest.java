package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.utils.AWSObjectStorageClientUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AWSObjectStorageClientIdentityPoliciesTest {
    static final String ROLE_ARN = "arn:aws:iam::442599936326:role/Pseudo-steam-bucket-identity-role";

    @Autowired
    private AWSObjectStorageClientUtils awsObjectStorageOriginal;

    @Value("${aws.region}")
    private String region;
    @Value("${aws.identity.policy.accessKeyId}")
    private String accessKeyId;
    @Value("${aws.identity.policy.secretAccessKey}")
    private String secretAccessKey;

    private String studioId;

    static final String singleUploadGameName = "gta-6";
    private String versionId;
    private AwsBasicCredentials awsBasicCredentials;

    @BeforeAll
    void beforeAll() {
        awsBasicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    }

    @Test
    @Order(1)
    void createBucketStudio() throws BucketNameExistsException {
        BucketsPage.Bucket studioBucket = awsObjectStorageOriginal.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();
        assertThat(studioBucket).isNotNull();
        assertThat(studioBucket.bucketName()).isNotNull().isNotBlank();

        System.out.println("Bucket created with name: " + studioId);
    }

    @Test
    @Order(2)
    void putObjectSinglePartUploadSuccessfully() throws IOException, BucketDoesNotExistException {
        Resource resource = new ClassPathResource("file_1mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);

        ObjectUploadResponse response = awsObjectStorageOriginal.putObjectSinglePartUpload(studioId, singleUploadGameName, multipartFile);
        versionId = response.versionId();

        assertThat(response).isNotNull();
        assertThat(response.gameName()).isEqualTo(singleUploadGameName);
        assertThat(response.studioId()).isEqualTo(studioId);
        assertThat(response.versionId()).isNotNull();

        System.out.println("Object upload with object key: " + response.gameName());
    }

    @Test
    @Order(3)
    void userWithoutRoleFails() {
        Region region = Region.of(this.region);

        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(awsBasicCredentials);
        S3Client s3Client = S3Client.builder().region(region).credentialsProvider(staticCredentialsProvider).build();
        S3Presigner s3Presigner = S3Presigner.builder().region(region).credentialsProvider(staticCredentialsProvider).build();
        AWSObjectStorageClientUtils awsObjectStorageClientUserNoRole = new AWSObjectStorageClientUtils(s3Client, s3Presigner);

        Assertions.assertThatThrownBy(() -> awsObjectStorageClientUserNoRole.getObject(studioId, singleUploadGameName, versionId)).isInstanceOf(S3Exception.class);
    }

    @Test
    @Order(4)
    void userWithRoleSucceeds() throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        Region region = Region.of(this.region);
        StsClient stsClient = StsClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
                .build();
        AssumeRoleRequest assumeRequest = AssumeRoleRequest.builder().roleArn(ROLE_ARN).roleSessionName("session-" + System.currentTimeMillis()).build();
        Credentials credentials = stsClient.assumeRole(assumeRequest).credentials();

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                credentials.sessionToken()
        );
        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(sessionCredentials);

        S3Client s3Client = S3Client.builder().region(region).credentialsProvider(staticCredentialsProvider).build();
        S3Presigner s3Presigner = S3Presigner.builder().region(region).credentialsProvider(staticCredentialsProvider).build();
        AWSObjectStorageClientUtils awsObjectStorageClientWithRole = new AWSObjectStorageClientUtils(s3Client, s3Presigner);

        awsObjectStorageClientWithRole.getObject(studioId, singleUploadGameName, versionId);

        Path downloadedFilePath = Path.of("downloads", singleUploadGameName);
        assertThat(Files.exists(downloadedFilePath)).isTrue();
    }

    @AfterAll
    void clean() throws BucketDoesNotExistException, IOException {
        awsObjectStorageOriginal.deleteBucket(studioId);
        Files.delete(Path.of("downloads", singleUploadGameName));
    }
}