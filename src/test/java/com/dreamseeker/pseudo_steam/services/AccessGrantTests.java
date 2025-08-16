package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.utils.AWSObjectStorageClientUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessGrantTests {

    private String studioId;

    @Autowired
    private Environment environment;

    static final String singleUploadGameName = "gta-6";

    @Autowired
    private AWSObjectStorageClientUtils awsObjectStorageOriginal;

    private S3ControlClient s3ControlClient;
    private final List<String> accessGrantIds = new ArrayList<>();
    private String locationId;

    @BeforeAll
    void setUp() {
        s3ControlClient = S3ControlClient.builder()
                .region(Region.of(environment.getProperty("aws.region")))
                .build();
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
    void createInstance() {
        CreateAccessGrantsInstanceRequest createRequest = CreateAccessGrantsInstanceRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .build();
        CreateAccessGrantsInstanceResponse response = s3ControlClient.createAccessGrantsInstance(createRequest);

        String expectedArn = "arn:aws:s3:" + environment.getProperty("aws.region") + ":" + environment.getProperty("aws.account") + ":access-grants/default";
        assertThat(response.accessGrantsInstanceArn()).isNotNull().isNotBlank().isEqualTo(expectedArn);
    }

    @Test
    @Order(3)
    void registerLocation() {
        CreateAccessGrantsLocationRequest createRequest = CreateAccessGrantsLocationRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .locationScope("s3://")
                .iamRoleArn("arn:aws:iam::442599936326:role/pseudo-steam-s3-acces-grant-role") //The role is restricted for access grants only
                .build();
        //requires user to have iam:PassRole permissions
        CreateAccessGrantsLocationResponse createResponse = s3ControlClient.createAccessGrantsLocation(createRequest);

        //location id is default because we assigned the root scope
        //if we selected a more specific scope, the id will be an uuid
        locationId = createResponse.accessGrantsLocationId();
        assertThat(locationId).isEqualTo("default");
        String expectedArn = "arn:aws:s3:" + environment.getProperty("aws.region") + ":" + environment.getProperty("aws.account") + ":access-grants/default/location/default";
        assertThat(createResponse.accessGrantsLocationArn()).isEqualTo(expectedArn);
    }

    @Test
    @Order(4)
    void createGrantForWrites() {
        //Sub-prefixes define the grant scope, rules apply in accordance with the location
        //https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-grants-grant-create.html
        AccessGrantsLocationConfiguration scope = AccessGrantsLocationConfiguration.builder()
                .s3SubPrefix(studioId.concat("/*"))
                .build();
        Grantee grantee = Grantee.builder()
                .granteeType("IAM")
                .granteeIdentifier("arn:aws:iam::442599936326:user/pseudo-steam-access-grant-write-user")
                .build();
        CreateAccessGrantRequest createRequest = CreateAccessGrantRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .accessGrantsLocationId(locationId)
                .permission(Permission.WRITE)
                .accessGrantsLocationConfiguration(scope)
                .grantee(grantee)
                .build();
        CreateAccessGrantResponse createResponse = s3ControlClient.createAccessGrant(createRequest);
        accessGrantIds.add(createResponse.accessGrantId());

        assertThat(createResponse).isNotNull();
        assertThat(createResponse.accessGrantsLocationId()).isEqualTo(locationId);
    }

    @Test
    @Order(5)
    void testUserForWrites() throws IOException, BucketDoesNotExistException {
        //requesting access with wrong user
        //privilege narrows the scope even more it can be:
        // DEFAULT, the scope is not narrowed further
        // MINIMAL, the scope is narrowed
        //https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-grants-credentials.html
        GetDataAccessRequest getDataAccessRequest = GetDataAccessRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .permission(Permission.WRITE)
                .privilege(Privilege.MINIMAL)
                .target("s3://" + studioId + "/*")
                .build();
        assertThatThrownBy(() -> s3ControlClient.getDataAccess(getDataAccessRequest))
                .isInstanceOf(AwsServiceException.class)
                .extracting(Throwable::getMessage)
                .satisfies(System.out::println);


        //requesting access with right user but wrong access level
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(environment.getProperty("aws.access.grant.wuser.access.key"), environment.getProperty("aws.access.grant.wuser.secret.access.key"))
        );
        try (S3ControlClient s3ControlWUserClient = S3ControlClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(environment.getProperty("aws.region")))
                .build()) {
            GetDataAccessRequest wrongGetDataAccessRequest = GetDataAccessRequest.builder()
                    .accountId(environment.getProperty("aws.account"))
                    .permission(Permission.READ)
                    .privilege(Privilege.MINIMAL)
                    .target("s3://" + studioId + "/*")
                    .build();
            //User requires data access permissions
            assertThatThrownBy(() -> s3ControlWUserClient.getDataAccess(wrongGetDataAccessRequest).credentials())
                    .isInstanceOf(AwsServiceException.class)
                    .extracting(Throwable::getMessage)
                    .satisfies(System.out::println);


            //requesting access with right user and access level
            Credentials credentials = s3ControlWUserClient.getDataAccess(getDataAccessRequest).credentials();
            AWSObjectStorageClientUtils objectStorageClient = createStorageClient(credentials);
            ObjectUploadResponse response = uploadObject(objectStorageClient);
            assertThat(response).isNotNull();

            //Attempting a read operation with user that only has write permissions
            assertThatThrownBy(() -> objectStorageClient.getObject(studioId, singleUploadGameName, null))
                    .isInstanceOf(AwsServiceException.class)
                    .extracting(Throwable::getMessage)
                    .satisfies(System.out::println);
        }
    }

    @Test
    @Order(6)
    void createGrantForReads() {
        AccessGrantsLocationConfiguration scope = AccessGrantsLocationConfiguration.builder()
                .s3SubPrefix(studioId.concat("/*"))
                .build();
        Grantee grantee = Grantee.builder().granteeType("IAM").granteeIdentifier("arn:aws:iam::442599936326:user/pseudo-steam-access-grant-read-user").build();
        CreateAccessGrantRequest createRequest = CreateAccessGrantRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .accessGrantsLocationId(locationId)
                .permission(Permission.READ)
                .accessGrantsLocationConfiguration(scope)
                .grantee(grantee)
                .build();
        CreateAccessGrantResponse createResponse = s3ControlClient.createAccessGrant(createRequest);
        accessGrantIds.add(createResponse.accessGrantId());

        assertThat(createResponse).isNotNull();
        assertThat(createResponse.accessGrantsLocationId()).isEqualTo(locationId);
    }

    @Test
    @Order(7)
    void testUserForReads() throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        GetDataAccessRequest getDataAccessRequest = GetDataAccessRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .permission(Permission.READ)
                .privilege(Privilege.MINIMAL)
                .target("s3://" + studioId + "/*")
                .build();
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(environment.getProperty("aws.access.grant.ruser.access.key"), environment.getProperty("aws.access.grant.ruser.secret.access.key"))
        );
        try (S3ControlClient s3ControlWUserClient = S3ControlClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(environment.getProperty("aws.region")))
                .build()) {
            Credentials credentials = s3ControlWUserClient.getDataAccess(getDataAccessRequest).credentials(); //It requires data access permissions for user
            AWSObjectStorageClientUtils objectStorageClient = createStorageClient(credentials);
            objectStorageClient.getObject(studioId, singleUploadGameName, null);
            Path downloadedFilePath = Path.of("downloads", singleUploadGameName);
            assertThat(Files.exists(downloadedFilePath)).isTrue();

            //attempt to do write operation with user who has only read permissions
            assertThatThrownBy(() -> uploadObject(objectStorageClient))
                    .isInstanceOf(AwsServiceException.class)
                    .extracting(Throwable::getMessage)
                    .satisfies(System.out::println);
        }
    }

    @AfterAll
    void afterAll() throws BucketDoesNotExistException, IOException {
        Files.delete(Path.of("downloads", singleUploadGameName));

        awsObjectStorageOriginal.deleteBucket(studioId);

        for (String accessGrantId : accessGrantIds)
            s3ControlClient.deleteAccessGrant(DeleteAccessGrantRequest.builder()
                    .accessGrantId(accessGrantId)
                    .accountId(environment.getProperty("aws.account"))
                    .build());

        DeleteAccessGrantsLocationRequest deleteAccessGrantsLocationRequest = DeleteAccessGrantsLocationRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .accessGrantsLocationId("default")
                .build();
        s3ControlClient.deleteAccessGrantsLocation(deleteAccessGrantsLocationRequest);

        s3ControlClient.deleteAccessGrantsInstance(DeleteAccessGrantsInstanceRequest.builder()
                .accountId(environment.getProperty("aws.account"))
                .build());
    }

    private ObjectUploadResponse uploadObject(AWSObjectStorageClientUtils awsObjectStorage) throws IOException, BucketDoesNotExistException {
        Resource resource = new ClassPathResource("file_1mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);
        return awsObjectStorage.putObjectSinglePartUpload(studioId, singleUploadGameName, multipartFile);
    }

    private AWSObjectStorageClientUtils createStorageClient(Credentials credentials) {
        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(
                        credentials.accessKeyId(),
                        credentials.secretAccessKey(),
                        credentials.sessionToken()
                )
        );
        S3Client s3Client = S3Client.builder().region(Region.of(environment.getProperty("aws.region"))).credentialsProvider(staticCredentialsProvider).build();
        S3Presigner s3Presigner = S3Presigner.builder().region(Region.of(environment.getProperty("aws.region"))).credentialsProvider(staticCredentialsProvider).build();
        return new AWSObjectStorageClientUtils(s3Client, s3Presigner);
    }
}
